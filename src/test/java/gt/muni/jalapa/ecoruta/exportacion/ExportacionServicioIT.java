package gt.muni.jalapa.ecoruta.exportacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-86: el administrador municipal descarga los datos de demanda y
 * recorridos en una hoja de calculo.
 *
 * <p>Los tres criterios de aceptacion: formato de hoja de calculo, rango de fechas
 * seleccionable, y que el archivo no exponga datos que identifiquen a un pasajero.
 * Los dias son de Guatemala (UTC-6): 2026-09-15 termina a las 06:00Z del 16.
 */
class ExportacionServicioIT extends IntegracionPostgisTest {

    private static final String URL = "/api/v1/admin/exportaciones/servicio";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** UUID de dispositivo como los que manda la app del pasajero. */
    private static final String DISPOSITIVO_A = "5b9f3c1e-7a44-4d0e-9d55-0a1b2c3d4e5f";
    private static final String DISPOSITIVO_B = "c0ffee00-1234-4abc-8def-abcdefabcdef";

    @Autowired
    private EmisorDeJwt emisor;

    // ---- Criterio 1: formato de hoja de calculo ------------------------------------------

    @Test
    void devuelve_un_xlsx_como_descarga_con_el_rango_en_el_nombre() throws Exception {
        var respuesta = mockMvc.perform(exportar("2026-09-01", "2026-09-15"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ecoruta-servicio_2026-09-01_a_2026-09-15.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();

        // Es un .xlsx de verdad: un ZIP que POI abre, con las tres hojas.
        try (Workbook libro = abrir(respuesta.getContentAsByteArray())) {
            assertThat(libro.getSheetName(0)).isEqualTo("Resumen");
            assertThat(libro.getSheetName(1)).isEqualTo("Demanda");
            assertThat(libro.getSheetName(2)).isEqualTo("Recorridos");
        }
    }

    @Test
    void la_demanda_sale_agrupada_por_dia_y_parada_con_el_conteo_de_cada_estado() throws Exception {
        // Dia 10 en Guatemala: 3 reservas en la parada 1 y 1 en la parada 2.
        reserva(DISPOSITIVO_A, 1, "ABORDO", "2026-09-10T18:00:00Z");
        reserva("otro-1", 1, "CANCELADA", "2026-09-10T18:05:00Z");
        reserva("otro-2", 1, "EXPIRADA", "2026-09-10T18:10:00Z");
        reserva("otro-3", 2, "ACTIVA", "2026-09-10T18:15:00Z");

        try (Workbook libro = descargar("2026-09-01", "2026-09-15")) {
            List<Row> filas = filasDeDatos(libro.getSheet("Demanda"));
            assertThat(filas).hasSize(2);

            Row parada1 = filas.get(0);
            assertThat(parada1.getCell(0).getLocalDateTimeCellValue().toLocalDate()).hasToString("2026-09-10");
            assertThat(parada1.getCell(2).getNumericCellValue()).isEqualTo(1);   // n.º de parada
            assertThat(parada1.getCell(4).getNumericCellValue()).isEqualTo(3);   // reservas
            assertThat(parada1.getCell(5).getNumericCellValue()).isEqualTo(1);   // abordaron
            assertThat(parada1.getCell(6).getNumericCellValue()).isEqualTo(1);   // canceladas
            assertThat(parada1.getCell(7).getNumericCellValue()).isEqualTo(1);   // expiradas
            assertThat(parada1.getCell(8).getNumericCellValue()).isZero();       // vigentes

            Row parada2 = filas.get(1);
            assertThat(parada2.getCell(4).getNumericCellValue()).isEqualTo(1);
            assertThat(parada2.getCell(8).getNumericCellValue()).isEqualTo(1);   // vigente
        }
    }

    @Test
    void los_recorridos_suman_la_distancia_entre_lecturas_y_omiten_los_huecos_de_senal() throws Exception {
        long bus = jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = 'BUS-01'", Long.class);
        // 0.009 grados de latitud son unos 1.0 km.
        posicion(bus, 14.6335, -89.9885, 20, "2026-09-10T18:00:00Z");
        posicion(bus, 14.6425, -89.9885, 30, "2026-09-10T18:01:00Z");
        // Una hora despues, lejos: es un hueco de senal, no un tramo recorrido.
        posicion(bus, 14.7000, -89.9885, 0, "2026-09-10T19:01:00Z");

        try (Workbook libro = descargar("2026-09-10", "2026-09-10")) {
            List<Row> filas = filasDeDatos(libro.getSheet("Recorridos"));
            assertThat(filas).hasSize(1);

            Row dia = filas.get(0);
            assertThat(dia.getCell(1).getStringCellValue()).isEqualTo("BUS-01");
            assertThat(dia.getCell(4).getNumericCellValue()).isEqualTo(3);        // lecturas
            assertThat(dia.getCell(7).getNumericCellValue()).isBetween(0.95, 1.05); // km
            // Solo cuentan las lecturas en movimiento: (20 + 30) / 2.
            assertThat(dia.getCell(8).getNumericCellValue()).isEqualTo(25.0);
        }
    }

    // ---- Criterio 2: rango de fechas seleccionable ---------------------------------------

    @Test
    void solo_entra_lo_que_cae_dentro_del_rango_y_los_extremos_son_inclusivos() throws Exception {
        reserva("antes", 1, "ABORDO", "2026-09-01T05:59:00Z");   // 31-ago 23:59 en Guatemala: fuera
        reserva("primero", 1, "ABORDO", "2026-09-01T06:00:00Z"); // 1-sep 00:00: dentro
        reserva("ultimo", 1, "ABORDO", "2026-09-16T05:59:00Z");  // 15-sep 23:59: dentro
        reserva("despues", 1, "ABORDO", "2026-09-16T06:00:00Z"); // 16-sep 00:00: fuera

        try (Workbook libro = descargar("2026-09-01", "2026-09-15")) {
            List<Row> filas = filasDeDatos(libro.getSheet("Demanda"));

            assertThat(filas).extracting(f -> f.getCell(0).getLocalDateTimeCellValue().toLocalDate().toString())
                    .containsExactly("2026-09-01", "2026-09-15");
            assertThat(valor(libro.getSheet("Resumen"), "Reservas creadas")).isEqualTo(2);
        }
    }

    @Test
    void cambiar_el_rango_cambia_el_contenido() throws Exception {
        reserva("dia-10", 1, "ABORDO", "2026-09-10T18:00:00Z");
        reserva("dia-20", 1, "ABORDO", "2026-09-20T18:00:00Z");

        try (Workbook primera = descargar("2026-09-01", "2026-09-15");
             Workbook segunda = descargar("2026-09-16", "2026-09-30")) {
            assertThat(filasDeDatos(primera.getSheet("Demanda"))).hasSize(1);
            assertThat(filasDeDatos(segunda.getSheet("Demanda"))).hasSize(1);
            assertThat(primera.getSheet("Demanda").getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .hasToString("2026-09-10");
            assertThat(segunda.getSheet("Demanda").getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .hasToString("2026-09-20");
        }
    }

    @Test
    void un_rango_sin_datos_devuelve_el_archivo_con_los_encabezados() throws Exception {
        try (Workbook libro = descargar("2026-01-01", "2026-01-31")) {
            assertThat(filasDeDatos(libro.getSheet("Demanda"))).isEmpty();
            assertThat(filasDeDatos(libro.getSheet("Recorridos"))).isEmpty();
            assertThat(valor(libro.getSheet("Resumen"), "Reservas creadas")).isZero();
        }
    }

    @Test
    void un_rango_invertido_responde_422_con_ApiError() throws Exception {
        mockMvc.perform(exportar("2026-09-15", "2026-09-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("desde")))
                .andExpect(jsonPath("$.path").value(URL));
    }

    @Test
    void un_rango_mayor_al_maximo_responde_422() throws Exception {
        mockMvc.perform(exportar("2024-01-01", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("366")));
    }

    @Test
    void sin_fechas_o_con_formato_invalido_responde_400_con_ApiError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(URL).header(AdminBootstrapFilter.CABECERA, ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("desde")));

        mockMvc.perform(exportar("15/09/2026", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("desde")));
    }

    // ---- Criterio 3: no expone datos que identifiquen a un pasajero -----------------------

    @Test
    void el_archivo_no_contiene_ningun_identificador_de_dispositivo() throws Exception {
        reserva(DISPOSITIVO_A, 1, "ABORDO", "2026-09-10T18:00:00Z");
        reserva(DISPOSITIVO_B, 2, "CANCELADA", "2026-09-10T18:30:00Z");
        jdbc.update("INSERT INTO dispositivos_notificacion (dispositivo_id, token) VALUES (?, ?)",
                DISPOSITIVO_A, "token-fcm-del-telefono-de-la-persona");

        byte[] archivo = mockMvc.perform(exportar("2026-09-01", "2026-09-15"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Se busca en TODO el contenido del .xlsx (cada parte XML del ZIP), no solo en
        // las celdas visibles: una columna oculta o una propiedad del documento contarian.
        String contenidoCompleto = todoElContenido(archivo);
        assertThat(contenidoCompleto)
                .isNotBlank()
                .doesNotContain(DISPOSITIVO_A)
                .doesNotContain(DISPOSITIVO_B)
                .doesNotContain("token-fcm-del-telefono-de-la-persona");

        // Y la demanda esta: el archivo no es inocuo por estar vacio.
        try (Workbook libro = abrir(archivo)) {
            assertThat(valor(libro.getSheet("Resumen"), "Reservas creadas")).isEqualTo(2);
        }
    }

    @Test
    void cada_fila_es_un_total_y_no_una_reserva_individual() throws Exception {
        for (int i = 0; i < 5; i++) {
            reserva("dispositivo-" + i, 1, "ABORDO", "2026-09-10T18:0" + i + ":00Z");
        }

        try (Workbook libro = descargar("2026-09-10", "2026-09-10")) {
            List<Row> filas = filasDeDatos(libro.getSheet("Demanda"));

            // Cinco reservas, una sola fila: no hay forma de distinguir a una persona.
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0).getCell(4).getNumericCellValue()).isEqualTo(5);
        }
    }

    // ---- Acceso: solo el administrador municipal ------------------------------------------

    @Test
    void sin_sesion_responde_401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(URL)
                        .param("desde", "2026-09-01").param("hasta", "2026-09-15"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void un_conductor_no_puede_exportar() throws Exception {
        String conductor = emisor.emitirParaConductor("uid-conductor-export").token();

        mockMvc.perform(MockMvcRequestBuilders.get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + conductor)
                        .param("desde", "2026-09-01").param("hasta", "2026-09-15"))
                .andExpect(status().isForbidden());
    }

    // ---- utilidades -------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder exportar(String desde, String hasta) {
        return MockMvcRequestBuilders.get(URL)
                .header(AdminBootstrapFilter.CABECERA, ADMIN)
                .param("desde", desde)
                .param("hasta", hasta);
    }

    private Workbook descargar(String desde, String hasta) throws Exception {
        byte[] archivo = mockMvc.perform(exportar(desde, hasta))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return abrir(archivo);
    }

    private static Workbook abrir(byte[] archivo) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(archivo));
    }

    /** Las filas debajo del encabezado. */
    private static List<Row> filasDeDatos(Sheet hoja) {
        List<Row> filas = new ArrayList<>();
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            filas.add(hoja.getRow(i));
        }
        return filas;
    }

    private static double valor(Sheet resumen, String etiqueta) {
        for (Row fila : resumen) {
            Cell primera = fila.getCell(0);
            if (primera != null && primera.getCellType() == CellType.STRING
                    && primera.getStringCellValue().equals(etiqueta)) {
                return fila.getCell(1).getNumericCellValue();
            }
        }
        throw new AssertionError("El resumen no tiene la etiqueta '" + etiqueta + "'");
    }

    private static String todoElContenido(byte[] xlsx) throws IOException {
        StringBuilder todo = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry parte = zip.getNextEntry(); parte != null; parte = zip.getNextEntry()) {
                todo.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return todo.toString();
    }

    private void reserva(String dispositivoId, long paradaId, String estado, String creadaEn) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz + interval '20 minutes')
                """, dispositivoId, paradaId, estado, creadaEn, creadaEn);
    }

    private void posicion(long vehiculoId, double latitud, double longitud, double velocidadKmh, String registradaEn) {
        jdbc.update("""
                INSERT INTO posiciones_historicas (ubicacion, velocidad_kmh, registrado_en, vehiculo_id)
                VALUES (ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?::timestamptz, ?)
                """, longitud, latitud, velocidadKmh, registradaEn, vehiculoId);
    }
}
