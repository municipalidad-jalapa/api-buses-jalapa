package gt.muni.jalapa.ecoruta.exportacion.servicio;

import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaDemanda;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaRecorrido;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El libro .xlsx de la exportacion (HU Desarrollo-86), leido de vuelta con POI. */
class LibroDelServicioTest {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 15);
    private static final Instant GENERADO = Instant.parse("2026-09-16T15:30:00Z");

    private static final FilaDemanda DEMANDA = new FilaDemanda(
            LocalDate.of(2026, 9, 10), 1L, "Ruta de ejemplo - Centro de Jalapa", 2, "Parque Central",
            12, 7, 2, 1, 2, 1);

    private static final FilaRecorrido RECORRIDO = new FilaRecorrido(
            LocalDate.of(2026, 9, 10), "BUS-01", "P-000BBB", 1L, "Ruta de ejemplo - Centro de Jalapa",
            1800, Instant.parse("2026-09-10T12:00:00Z"), Instant.parse("2026-09-10T20:00:00Z"),
            41250.0, 24.5, 9);

    @Test
    void trae_tres_hojas_en_el_orden_resumen_demanda_recorridos() throws IOException {
        try (Workbook libro = abrir(armar(List.of(DEMANDA), List.of(RECORRIDO)))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(3);
            assertThat(libro.getSheetName(0)).isEqualTo("Resumen");
            assertThat(libro.getSheetName(1)).isEqualTo("Demanda");
            assertThat(libro.getSheetName(2)).isEqualTo("Recorridos");
        }
    }

    @Test
    void la_hoja_de_demanda_lleva_una_fila_por_dia_y_parada_con_numeros_y_fechas_reales() throws IOException {
        try (Workbook libro = abrir(armar(List.of(DEMANDA), List.of()))) {
            Sheet hoja = libro.getSheet("Demanda");
            assertThat(textos(hoja.getRow(0))).isEqualTo(LibroDelServicio.COLUMNAS_DEMANDA);

            Row fila = hoja.getRow(1);
            // Fecha y conteos son de Excel, no texto: se pueden filtrar, sumar y pivotar.
            assertThat(fila.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(fila.getCell(0))).isTrue();
            assertThat(fila.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(DEMANDA.fecha());
            assertThat(fila.getCell(1).getStringCellValue()).isEqualTo("Ruta de ejemplo - Centro de Jalapa");
            assertThat(fila.getCell(3).getStringCellValue()).isEqualTo("Parque Central");
            assertThat(fila.getCell(4).getNumericCellValue()).isEqualTo(12);
            assertThat(fila.getCell(5).getNumericCellValue()).isEqualTo(7);
            assertThat(fila.getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(fila.getCell(7).getNumericCellValue()).isEqualTo(1);
            assertThat(fila.getCell(8).getNumericCellValue()).isEqualTo(2);
            assertThat(fila.getCell(9).getNumericCellValue()).isEqualTo(1);
        }
    }

    @Test
    void la_hoja_de_recorridos_convierte_metros_a_km_y_las_horas_a_la_zona_de_guatemala() throws IOException {
        try (Workbook libro = abrir(armar(List.of(), List.of(RECORRIDO)))) {
            Sheet hoja = libro.getSheet("Recorridos");
            assertThat(textos(hoja.getRow(0))).isEqualTo(LibroDelServicio.COLUMNAS_RECORRIDOS);

            Row fila = hoja.getRow(1);
            assertThat(fila.getCell(1).getStringCellValue()).isEqualTo("BUS-01");
            assertThat(fila.getCell(4).getNumericCellValue()).isEqualTo(1800);
            // 12:00Z y 20:00Z son las 06:00 y las 14:00 en Guatemala (UTC-6, sin horario de verano).
            assertThat(fila.getCell(5).getLocalDateTimeCellValue().toLocalTime()).hasToString("06:00");
            assertThat(fila.getCell(6).getLocalDateTimeCellValue().toLocalTime()).hasToString("14:00");
            assertThat(fila.getCell(7).getNumericCellValue()).isEqualTo(41.25);
            assertThat(fila.getCell(8).getNumericCellValue()).isEqualTo(24.5);
            assertThat(fila.getCell(9).getNumericCellValue()).isEqualTo(9);
        }
    }

    @Test
    void un_bus_sin_ruta_ni_movimiento_se_ve_claro_y_no_rompe_el_archivo() throws IOException {
        FilaRecorrido quieto = new FilaRecorrido(LocalDate.of(2026, 9, 10), "BUS-09", "P-000ZZZ", null, null,
                3, Instant.parse("2026-09-10T12:00:00Z"), Instant.parse("2026-09-10T12:00:20Z"), 0.0, null, 0);

        try (Workbook libro = abrir(armar(List.of(), List.of(quieto)))) {
            Row fila = libro.getSheet("Recorridos").getRow(1);
            assertThat(fila.getCell(3).getStringCellValue()).isEqualTo("Sin ruta asignada");
            // Sin velocidad en movimiento la celda queda vacia, no en cero: cero mentiria.
            assertThat(fila.getCell(8).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void el_resumen_suma_los_totales_y_muestra_el_periodo() throws IOException {
        FilaDemanda otra = new FilaDemanda(LocalDate.of(2026, 9, 11), 1L, "Ruta", 1, "Parada", 3, 1, 1, 1, 0, 0);

        try (Workbook libro = abrir(armar(List.of(DEMANDA, otra), List.of(RECORRIDO)))) {
            Sheet resumen = libro.getSheet("Resumen");

            assertThat(valorDe(resumen, "Desde").getLocalDateTimeCellValue().toLocalDate()).isEqualTo(DESDE);
            assertThat(valorDe(resumen, "Hasta (inclusive)").getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(HASTA);
            assertThat(valorDe(resumen, "Zona horaria de los días").getStringCellValue())
                    .isEqualTo("America/Guatemala");
            assertThat(valorDe(resumen, "Reservas creadas").getNumericCellValue()).isEqualTo(15);
            assertThat(valorDe(resumen, "Abordaron").getNumericCellValue()).isEqualTo(8);
            assertThat(valorDe(resumen, "Canceladas").getNumericCellValue()).isEqualTo(3);
            assertThat(valorDe(resumen, "Expiradas").getNumericCellValue()).isEqualTo(2);
            assertThat(valorDe(resumen, "Vigentes al exportar").getNumericCellValue()).isEqualTo(2);
            assertThat(valorDe(resumen, "Lecturas GPS").getNumericCellValue()).isEqualTo(1800);
            assertThat(valorDe(resumen, "Distancia estimada (km)").getNumericCellValue()).isEqualTo(41.25);
        }
    }

    @Test
    void sin_datos_en_el_rango_el_archivo_sale_igual_con_encabezados_y_totales_en_cero() throws IOException {
        try (Workbook libro = abrir(armar(List.of(), List.of()))) {
            assertThat(libro.getSheet("Demanda").getLastRowNum()).isZero();
            assertThat(libro.getSheet("Recorridos").getLastRowNum()).isZero();
            assertThat(valorDe(libro.getSheet("Resumen"), "Reservas creadas").getNumericCellValue()).isZero();
        }
    }

    @Test
    void un_nombre_que_parece_formula_queda_como_texto_y_no_se_ejecuta() throws IOException {
        FilaDemanda hostil = new FilaDemanda(LocalDate.of(2026, 9, 10), 1L, "=HYPERLINK(\"http://x\")", 1,
                "+cmd|' /C calc'!A0", 1, 0, 0, 0, 1, 0);

        try (Workbook libro = abrir(armar(List.of(hostil), List.of()))) {
            Row fila = libro.getSheet("Demanda").getRow(1);
            assertThat(fila.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(fila.getCell(3).getCellType()).isEqualTo(CellType.STRING);
        }
    }

    @Test
    void ninguna_columna_del_archivo_nombra_a_un_pasajero_ni_su_dispositivo() {
        List<String> encabezados = new ArrayList<>(LibroDelServicio.COLUMNAS_DEMANDA);
        encabezados.addAll(LibroDelServicio.COLUMNAS_RECORRIDOS);

        assertThat(String.join("|", encabezados).toLowerCase())
                .doesNotContain("dispositivo")
                .doesNotContain("pasajero")
                .doesNotContain("token")
                .doesNotContain("uuid")
                .doesNotContain("conductor");
    }

    private static byte[] armar(List<FilaDemanda> demanda, List<FilaRecorrido> recorridos) {
        return LibroDelServicio.armar(DESDE, HASTA, GUATEMALA, GENERADO, demanda, recorridos);
    }

    private static Workbook abrir(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    private static List<String> textos(Row fila) {
        List<String> textos = new ArrayList<>();
        for (Cell celda : fila) {
            textos.add(celda.getStringCellValue());
        }
        return textos;
    }

    /** La celda a la derecha de la etiqueta indicada, en la columna A del resumen. */
    private static Cell valorDe(Sheet hoja, String etiqueta) {
        for (Row fila : hoja) {
            Cell primera = fila.getCell(0);
            if (primera != null && primera.getCellType() == CellType.STRING
                    && primera.getStringCellValue().equals(etiqueta)) {
                return fila.getCell(1);
            }
        }
        throw new AssertionError("El resumen no tiene la etiqueta '" + etiqueta + "'");
    }
}
