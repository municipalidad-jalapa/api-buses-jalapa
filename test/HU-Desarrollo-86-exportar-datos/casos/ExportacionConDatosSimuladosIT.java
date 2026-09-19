package gt.muni.jalapa.ecoruta.exportacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.abrir;
import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.filasDeDatos;
import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.todoElContenido;
import static gt.muni.jalapa.ecoruta.exportacion.LectorDeXlsx.valorDelResumen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-86 con DATOS SIMULADOS y en volumen: 30 dias, 2 buses, 72 000
 * lecturas GPS y 400 reservas generadas con una semilla fija (siempre los mismos
 * datos, asi que el resultado es reproducible).
 *
 * <p>La respuesta esperada NO sale de la misma consulta SQL que se prueba: se calcula
 * aparte, en Java, a partir de los datos que se generaron. Si la agrupacion por dia,
 * el cambio de zona horaria o la suma de distancia tuvieran un error, las dos cifras
 * no coincidirian.
 */
class ExportacionConDatosSimuladosIT extends IntegracionPostgisTest {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final LocalDate DESDE = LocalDate.of(2026, 8, 16);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 14); // 30 dias

    private static final int LECTURAS_POR_DIA = 1200;
    private static final int LECTURA_DEL_HUECO = 600;
    private static final int SEGUNDOS_ENTRE_LECTURAS = 4;
    private static final double PASO_EN_GRADOS = 0.00005; // unos 5.5 m por lectura
    private static final double SALTO_DEL_HUECO_EN_GRADOS = 0.02;
    private static final int RESERVAS = 400;

    private static final String[] ESTADOS = {"ABORDO", "ABORDO", "ABORDO", "CANCELADA", "EXPIRADA", "ACTIVA", "RENOVADA"};

    /** Conteos esperados de una parada en un dia. */
    private static final class Conteo {
        long reservas, abordaron, canceladas, expiradas, vigentes, noAbordaron;
    }

    private record Parada(long id, int orden, String ruta) {
    }

    @Test
    void un_mes_de_datos_simulados_da_los_mismos_totales_que_el_calculo_independiente() throws Exception {
        List<Parada> paradas = jdbc.query("""
                SELECT p.id, p.orden, r.nombre FROM paradas p JOIN rutas r ON r.id = p.ruta_id ORDER BY p.id
                """, (rs, i) -> new Parada(rs.getLong(1), rs.getInt(2), rs.getString(3)));
        Random azar = new Random(86);

        // ---- Reservas: esperado calculado en Java ----
        List<Object[]> filas = new ArrayList<>();
        Map<String, Conteo> esperado = new HashMap<>();
        List<String> dispositivos = new ArrayList<>();
        for (int i = 0; i < RESERVAS; i++) {
            Instant creada = DESDE.atStartOfDay(ZoneId.of("UTC")).toInstant()
                    .plus(Duration.ofMinutes(azar.nextInt(30 * 24 * 60)));
            Parada parada = paradas.get(azar.nextInt(paradas.size()));
            String estado = ESTADOS[azar.nextInt(ESTADOS.length)];
            boolean declaroNoAbordo = azar.nextInt(10) == 0;
            String dispositivo = new UUID(azar.nextLong(), azar.nextLong()).toString();
            dispositivos.add(dispositivo);
            filas.add(new Object[]{dispositivo, parada.id(), estado, Timestamp.from(creada),
                    Timestamp.from(creada.plusSeconds(1200)), declaroNoAbordo});

            // Solo cuenta lo que cae dentro del rango EN DIAS DE GUATEMALA: las primeras 6 h UTC
            // del primer dia pertenecen al dia anterior, y las ultimas 6 h del rango, al siguiente.
            LocalDate dia = creada.atZone(GUATEMALA).toLocalDate();
            if (dia.isBefore(DESDE) || dia.isAfter(HASTA)) {
                continue;
            }
            Conteo c = esperado.computeIfAbsent(dia + "|" + parada.id(), k -> new Conteo());
            c.reservas++;
            switch (estado) {
                case "ABORDO" -> c.abordaron++;
                case "CANCELADA" -> c.canceladas++;
                case "EXPIRADA" -> c.expiradas++;
                default -> c.vigentes++;
            }
            if (declaroNoAbordo) {
                c.noAbordaron++;
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en,
                                              pasajero_declaro_no_abordo)
                VALUES (?, ?, ?, ?, ?, ?)
                """, filas);

        // ---- Posiciones GPS: 2 buses x 30 dias x 1200 lecturas, con un hueco de senal cada dia ----
        long bus1 = jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = 'BUS-01'", Long.class);
        long bus2 = jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = 'BUS-02'", Long.class);
        double metrosEsperadosPorDia = metrosEsperadosPorDia();
        double velocidadPromedioEsperada = velocidadPromedioEsperada();

        List<Object[]> posiciones = new ArrayList<>();
        for (LocalDate dia = DESDE; !dia.isAfter(HASTA); dia = dia.plusDays(1)) {
            Instant inicio = dia.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant(); // 06:00 en Guatemala
            for (long bus : new long[]{bus1, bus2}) {
                double latitudInicial = bus == bus1 ? 14.6335 : 14.6400;
                for (int i = 0; i < LECTURAS_POR_DIA; i++) {
                    boolean despuesDelHueco = i >= LECTURA_DEL_HUECO;
                    double latitud = latitudInicial + i * PASO_EN_GRADOS
                            + (despuesDelHueco ? SALTO_DEL_HUECO_EN_GRADOS : 0);
                    long segundos = (long) i * SEGUNDOS_ENTRE_LECTURAS + (despuesDelHueco ? 7200 : 0);
                    posiciones.add(new Object[]{-89.9885, latitud, velocidadEnLectura(i),
                            Timestamp.from(inicio.plusSeconds(segundos)), bus});
                }
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO posiciones_historicas (ubicacion, velocidad_kmh, registrado_en, vehiculo_id)
                VALUES (ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?)
                """, posiciones);

        // ---- Exportar el mes completo y medir ----
        long inicioMedicion = System.nanoTime();
        byte[] archivo = mockMvc.perform(get("/api/v1/admin/exportaciones/servicio")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN)
                        .param("desde", DESDE.toString()).param("hasta", HASTA.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Duration tardo = Duration.ofNanos(System.nanoTime() - inicioMedicion);
        System.out.printf("[HU-86] Exportar 30 dias con %d lecturas y %d reservas tardo %d ms (%d KB)%n",
                posiciones.size(), RESERVAS, tardo.toMillis(), archivo.length / 1024);
        assertThat(tardo).as("la exportacion de un mes no debe tardar mas de 30 s").isLessThan(Duration.ofSeconds(30));

        try (Workbook libro = abrir(archivo)) {
            // ---- Demanda: fila por fila contra el calculo independiente ----
            List<Row> demanda = filasDeDatos(libro.getSheet("Demanda"));
            assertThat(demanda).hasSameSizeAs(esperado.keySet());
            long totalReservas = 0;
            for (Row fila : demanda) {
                LocalDate dia = fila.getCell(0).getLocalDateTimeCellValue().toLocalDate();
                Parada parada = paradas.stream()
                        .filter(p -> p.ruta().equals(fila.getCell(1).getStringCellValue())
                                && p.orden() == (int) fila.getCell(2).getNumericCellValue())
                        .findFirst().orElseThrow();
                Conteo c = esperado.get(dia + "|" + parada.id());
                assertThat(c).as("fila inesperada: %s parada %d", dia, parada.id()).isNotNull();

                assertThat(fila.getCell(4).getNumericCellValue()).as("reservas %s p%d", dia, parada.id()).isEqualTo(c.reservas);
                assertThat(fila.getCell(5).getNumericCellValue()).as("abordaron %s p%d", dia, parada.id()).isEqualTo(c.abordaron);
                assertThat(fila.getCell(6).getNumericCellValue()).as("canceladas %s p%d", dia, parada.id()).isEqualTo(c.canceladas);
                assertThat(fila.getCell(7).getNumericCellValue()).as("expiradas %s p%d", dia, parada.id()).isEqualTo(c.expiradas);
                assertThat(fila.getCell(8).getNumericCellValue()).as("vigentes %s p%d", dia, parada.id()).isEqualTo(c.vigentes);
                assertThat(fila.getCell(9).getNumericCellValue()).as("no abordaron %s p%d", dia, parada.id()).isEqualTo(c.noAbordaron);
                totalReservas += c.reservas;
            }
            assertThat(valorDelResumen(libro.getSheet("Resumen"), "Reservas creadas")).isEqualTo(totalReservas);
            assertThat(totalReservas).as("el filtro por dia de Guatemala dejo fuera las de los extremos")
                    .isLessThan(RESERVAS);

            // ---- Recorridos: 30 dias x 2 buses ----
            List<Row> recorridos = filasDeDatos(libro.getSheet("Recorridos"));
            assertThat(recorridos).hasSize(60);
            for (Row fila : recorridos) {
                assertThat(fila.getCell(4).getNumericCellValue()).isEqualTo(LECTURAS_POR_DIA);
                // El salto de 2 h del hueco NO suma: solo cuentan los tramos consecutivos.
                assertThat(fila.getCell(7).getNumericCellValue())
                        .isCloseTo(metrosEsperadosPorDia / 1000.0, withPercentage(1.0));
                assertThat(fila.getCell(8).getNumericCellValue()).isCloseTo(velocidadPromedioEsperada,
                        offset(1e-9));
            }
            assertThat(valorDelResumen(libro.getSheet("Resumen"), "Lecturas GPS")).isEqualTo(posiciones.size());
            assertThat(valorDelResumen(libro.getSheet("Resumen"), "Distancia estimada (km)"))
                    .isCloseTo(60 * metrosEsperadosPorDia / 1000.0, withPercentage(1.0));
        }

        // ---- Privacidad a escala: ninguno de los 400 identificadores de dispositivo aparece ----
        String contenido = todoElContenido(archivo);
        for (String dispositivo : dispositivos) {
            assertThat(contenido).doesNotContain(dispositivo);
        }
    }

    /** Cada 10.a lectura el bus esta detenido (0 km/h); el resto va entre 18 y 22 km/h. */
    private static double velocidadEnLectura(int i) {
        return i % 10 == 0 ? 0.0 : 18 + (i % 5);
    }

    /** El promedio "en movimiento" ignora las lecturas a 0 km/h. */
    private static double velocidadPromedioEsperada() {
        double suma = 0;
        int cuantas = 0;
        for (int i = 0; i < LECTURAS_POR_DIA; i++) {
            double v = velocidadEnLectura(i);
            if (v > 0) {
                suma += v;
                cuantas++;
            }
        }
        return suma / cuantas;
    }

    /**
     * Metros que recorre un bus en un dia, midiendo aparte (haversine) los tramos entre lecturas
     * consecutivas, SIN el salto del hueco. Va sobre un meridiano, asi que solo cuenta la latitud.
     */
    private static double metrosEsperadosPorDia() {
        double radioDeLaTierra = 6_371_008.8;
        double metrosPorPaso = Math.toRadians(PASO_EN_GRADOS) * radioDeLaTierra;
        // 1199 tramos entre 1200 lecturas, menos el tramo que cruza el hueco.
        return (LECTURAS_POR_DIA - 1 - 1) * metrosPorPaso;
    }
}
