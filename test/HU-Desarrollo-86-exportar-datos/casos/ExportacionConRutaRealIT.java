package gt.muni.jalapa.ecoruta.exportacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.abrir;
import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.filasDeDatos;
import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.valorDelResumen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-86 con DATOS REALES: la geometria de las rutas sembradas por
 * Flyway (V6 y V12), cuyos vertices salen de OpenStreetMap enrutados sobre las
 * calles de Jalapa. Las posiciones del bus son los propios vertices del trazado,
 * asi que la distancia que exporta la API tiene una respuesta independiente y
 * exacta: el largo de la linea, medido por PostGIS (ST_Length sobre geography).
 *
 * <p>Ojo: el recorrido es real pero el circuito no es la ruta oficial (V6 lo dice:
 * es un ejemplo sobre calles verdaderas). Lo que se valida es el calculo, no la ruta.
 */
class ExportacionConRutaRealIT extends IntegracionPostgisTest {

    private static final String URL = "/api/v1/admin/exportaciones/servicio";
    private static final String CONDUCTOR_DE_PRUEBA = "it-exportacion";

    @AfterEach
    void limpiarParadasAtendidas() {
        // limpiarDatosDePrueba no toca paradas_atendidas: se limpia lo propio.
        jdbc.update("DELETE FROM paradas_atendidas WHERE conductor_username = ?", CONDUCTOR_DE_PRUEBA);
    }

    @Test
    void la_distancia_exportada_de_cada_ruta_real_coincide_con_el_largo_de_su_trazado() throws Exception {
        Map<String, Object> ruta1 = jdbc.queryForMap("SELECT id, nombre FROM rutas WHERE id = 1");
        Map<String, Object> ruta2 = jdbc.queryForMap(
                "SELECT id, nombre FROM rutas WHERE nombre LIKE 'Ruta de prueba - Parque Central a Metroplaza'");

        int lecturas1 = simularUnaVuelta((Long) ruta1.get("id"), "BUS-01");
        int lecturas2 = simularUnaVuelta((Long) ruta2.get("id"), "BUS-02");
        double km1 = largoDelTrazadoEnKm((Long) ruta1.get("id"));
        double km2 = largoDelTrazadoEnKm((Long) ruta2.get("id"));
        assertThat(km1).as("la ruta 1 mide unos 5 km").isBetween(4.0, 7.0);

        try (Workbook libro = descargar()) {
            List<Row> filas = filasDeDatos(libro.getSheet("Recorridos"));
            assertThat(filas).hasSize(2);

            Row bus1 = filas.get(0);
            assertThat(bus1.getCell(1).getStringCellValue()).isEqualTo("BUS-01");
            assertThat(bus1.getCell(3).getStringCellValue()).isEqualTo(ruta1.get("nombre"));
            assertThat(bus1.getCell(4).getNumericCellValue()).isEqualTo(lecturas1);
            // Cada lectura es un vertice: sumar tramos da el largo de la linea, con un
            // margen minimo por el redondeo entre dos metodos de medir sobre el elipsoide.
            assertThat(bus1.getCell(7).getNumericCellValue()).isCloseTo(km1, withPercentage(0.5));

            Row bus2 = filas.get(1);
            assertThat(bus2.getCell(1).getStringCellValue()).isEqualTo("BUS-02");
            assertThat(bus2.getCell(4).getNumericCellValue()).isEqualTo(lecturas2);
            assertThat(bus2.getCell(7).getNumericCellValue()).isCloseTo(km2, withPercentage(0.5));

            assertThat(valorDelResumen(libro.getSheet("Resumen"), "Distancia estimada (km)"))
                    .isCloseTo(km1 + km2, withPercentage(0.5));
            assertThat(valorDelResumen(libro.getSheet("Resumen"), "Lecturas GPS"))
                    .isEqualTo(lecturas1 + lecturas2);
        }
    }

    @Test
    void las_paradas_reales_de_una_ruta_aparecen_una_por_una_con_su_numero_y_nombre() throws Exception {
        List<Map<String, Object>> paradas = jdbc.queryForList(
                "SELECT id, orden, nombre FROM paradas WHERE ruta_id = 1 ORDER BY orden");
        assertThat(paradas).hasSize(8);
        for (Map<String, Object> parada : paradas) {
            jdbc.update("""
                    INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                    VALUES (?, ?, 'ABORDO', '2026-09-10T18:00:00Z', '2026-09-10T18:20:00Z')
                    """, "real-" + parada.get("id"), parada.get("id"));
        }

        try (Workbook libro = descargar()) {
            List<Row> filas = filasDeDatos(libro.getSheet("Demanda"));

            assertThat(filas).hasSize(8);
            for (int i = 0; i < 8; i++) {
                Row fila = filas.get(i);
                assertThat(fila.getCell(2).getNumericCellValue()).isEqualTo(((Number) paradas.get(i).get("orden")).doubleValue());
                assertThat(fila.getCell(3).getStringCellValue()).isEqualTo(paradas.get(i).get("nombre"));
                assertThat(fila.getCell(4).getNumericCellValue()).isEqualTo(1);
                assertThat(fila.getCell(5).getNumericCellValue()).isEqualTo(1);
            }
        }
    }

    @Test
    void las_paradas_atendidas_por_el_conductor_se_cuentan_por_ruta_y_dia() throws Exception {
        simularUnaVuelta(1L, "BUS-01");
        List<Long> paradas = jdbc.queryForList("SELECT id FROM paradas WHERE ruta_id = 1", Long.class);
        for (Long parada : paradas) {
            jdbc.update("""
                    INSERT INTO paradas_atendidas (ruta_id, parada_id, conductor_username, fecha_servicio, marcada_en)
                    VALUES (1, ?, ?, '2026-09-10', '2026-09-10T18:00:00Z')
                    """, parada, CONDUCTOR_DE_PRUEBA);
        }

        try (Workbook libro = descargar()) {
            Row bus1 = filasDeDatos(libro.getSheet("Recorridos")).get(0);
            assertThat(bus1.getCell(9).getNumericCellValue()).isEqualTo(paradas.size());
        }
        // El nombre del conductor es dato de un empleado y tampoco viaja en el archivo.
        byte[] archivo = bytes();
        assertThat(LectorDeXlsx.todoElContenido(archivo)).doesNotContain(CONDUCTOR_DE_PRUEBA);
    }

    // ------------------------------------------------------------------------------------

    /** Inserta cada vertice del trazado como una lectura GPS, cada 2 s. Devuelve cuantas. */
    private int simularUnaVuelta(long rutaId, String bus) {
        long vehiculoId = jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = ?", Long.class, bus);
        return jdbc.update("""
                INSERT INTO posiciones_historicas (ubicacion, velocidad_kmh, registrado_en, vehiculo_id)
                SELECT (v.dp).geom, 30,
                       TIMESTAMPTZ '2026-09-10 12:00:00+00' + (v.n - 1) * INTERVAL '2 seconds',
                       ?
                  FROM (SELECT dp, row_number() OVER (ORDER BY (dp).path[1]) AS n
                          FROM (SELECT ST_DumpPoints(trazado) AS dp FROM rutas WHERE id = ?) t) v
                """, vehiculoId, rutaId);
    }

    private double largoDelTrazadoEnKm(long rutaId) {
        return jdbc.queryForObject("SELECT ST_Length(trazado::geography) / 1000.0 FROM rutas WHERE id = ?",
                Double.class, rutaId);
    }

    private byte[] bytes() throws Exception {
        return mockMvc.perform(get(URL)
                        .header(AdminBootstrapFilter.CABECERA, ADMIN)
                        .param("desde", "2026-09-10").param("hasta", "2026-09-10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private Workbook descargar() throws Exception {
        return abrir(bytes());
    }
}
