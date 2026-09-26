package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pasos de {@code exportar_los_datos_del_servicio.feature} (HU Desarrollo-86).
 *
 * <p>La respuesta la guarda {@link ContextoDelEscenario}; "la respuesta tiene
 * codigo N" es un paso compartido de {@code PasosDeDemanda}. El archivo se lee de
 * vuelta con POI, que es lo que hara la Municipalidad con Excel.
 */
public class PasosDeExportacion {

    private static final String URL = "/api/v1/admin/exportaciones/servicio";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Dado("que el dispositivo {string} reservó el {string}")
    public void reservo_el(String dispositivoId, String dia) {
        // A las 18:00Z son las 12:00 en Guatemala: lejos de la medianoche de cualquier dia.
        String creadaEn = dia + "T18:00:00Z";
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, 1, 'ABORDO', ?::timestamptz, ?::timestamptz + interval '20 minutes')
                """, dispositivoId, creadaEn, creadaEn);
    }

    @Cuando("el administrador exporta los datos del servicio desde {string} hasta {string}")
    public void exporta(String desde, String hasta) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get(URL)
                .header(AdminBootstrapFilter.CABECERA, IntegracionPostgisTest.ADMIN)
                .param("desde", desde)
                .param("hasta", hasta)));
    }

    @Cuando("un pasajero sin sesión intenta exportar los datos del servicio")
    public void pasajero_intenta_exportar() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get(URL)
                .param("desde", "2026-09-01")
                .param("hasta", "2026-09-15")));
    }

    @Y("recibe una hoja de cálculo con las hojas {string}, {string} y {string}")
    public void recibe_hoja_de_calculo(String primera, String segunda, String tercera) throws Exception {
        try (Workbook libro = libro()) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(3);
            assertThat(libro.getSheetName(0)).isEqualTo(primera);
            assertThat(libro.getSheetName(1)).isEqualTo(segunda);
            assertThat(libro.getSheetName(2)).isEqualTo(tercera);
        }
    }

    @Y("la hoja {string} tiene {int} fila de datos")
    public void hoja_con_filas(String hoja, int filas) throws Exception {
        try (Workbook libro = libro()) {
            // La fila 0 es el encabezado.
            assertThat(libro.getSheet(hoja).getLastRowNum()).isEqualTo(filas);
        }
    }

    @Y("la hoja {string} muestra la fecha {string}")
    public void hoja_muestra_fecha(String hoja, String fecha) throws Exception {
        try (Workbook libro = libro()) {
            assertThat(libro.getSheet(hoja).getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .hasToString(fecha);
        }
    }

    @Y("el archivo no contiene el identificador {string}")
    public void archivo_sin_identificador(String identificador) throws Exception {
        // En TODAS las partes del .xlsx, no solo en las celdas visibles.
        assertThat(todoElContenido()).isNotBlank().doesNotContain(identificador);
    }

    private byte[] archivo() throws Exception {
        return contexto.ultimaRespuesta().andReturn().getResponse().getContentAsByteArray();
    }

    private Workbook libro() throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(archivo()));
    }

    private String todoElContenido() throws Exception {
        StringBuilder todo = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archivo()))) {
            for (ZipEntry parte = zip.getNextEntry(); parte != null; parte = zip.getNextEntry()) {
                todo.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new AssertionError("La respuesta no es un .xlsx (ZIP) valido", e);
        }
        return todo.toString();
    }
}
