package gt.muni.jalapa.ecoruta.aceptacion;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pasos de {@code datos_reales_de_las_rutas_de_jalapa.feature} (SCRUM-136 / HU-41).
 *
 * <p>Las coordenadas exactas se validan en {@code DatosRealesRutasIT}; aqui se
 * comprueba el comportamiento funcional sin decimales en Gherkin (locale ES).
 */
public class PasosDeDatosRealesDeRuta {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    private String rutaConsultada;
    private Long rutaIdConsultada;

    @Cuando("alguien consulta las rutas disponibles")
    public void alguien_consulta_las_rutas_disponibles() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/rutas")));
        contexto.ultimaRespuesta().andExpect(status().isOk());
    }

    @Cuando("alguien consulta la ruta {string}")
    public void alguien_consulta_la_ruta(String nombre) throws Exception {
        rutaConsultada = nombre;
        rutaIdConsultada = jdbc.queryForObject(
                "SELECT id FROM rutas WHERE nombre = ?", Long.class, nombre);
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/rutas/" + rutaIdConsultada)));
        contexto.ultimaRespuesta().andExpect(status().isOk());
    }

    @Cuando("se inspeccionan las coordenadas almacenadas de las rutas")
    public void se_inspeccionan_las_coordenadas_almacenadas() {
        // La inspeccion es sobre PostGIS; el Entonces valida el resultado.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rutas WHERE activa", Integer.class))
                .isEqualTo(2);
    }

    @Dado("que existen las dos rutas activas")
    public void que_existen_las_dos_rutas_activas() {
        List<String> nombres = jdbc.queryForList(
                "SELECT nombre FROM rutas WHERE activa ORDER BY id", String.class);
        assertThat(nombres).containsExactly("RUTA PRINCIPAL", "RUTA SECUNDARIA");
    }

    @Entonces("la ruta {string} contiene {int} paradas ordenadas")
    public void la_ruta_contiene_paradas_ordenadas(String nombre, int cuantas) throws Exception {
        List<Map<String, Object>> rutas = com.jayway.jsonpath.JsonPath.read(cuerpo(), "$");
        Map<String, Object> ruta = rutas.stream()
                .filter(r -> nombre.equals(r.get("nombre")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) ruta.get("paradas");
        assertThat(lista).hasSize(cuantas);
        for (int i = 0; i < cuantas; i++) {
            assertThat(((Number) lista.get(i).get("orden")).intValue()).isEqualTo(i + 1);
        }
    }

    @Y("sus paradas se llaman desde {string} hasta {string}")
    public void sus_paradas_se_llaman_desde_hasta(String primera, String ultima) throws Exception {
        List<Map<String, Object>> rutas = com.jayway.jsonpath.JsonPath.read(cuerpo(), "$");
        Map<String, Object> ruta = rutas.stream()
                .filter(r -> "RUTA PRINCIPAL".equals(r.get("nombre")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) ruta.get("paradas");
        assertThat(lista.get(0).get("nombre")).isEqualTo(primera);
        assertThat(lista.get(lista.size() - 1).get("nombre")).isEqualTo(ultima);
        for (int i = 0; i < lista.size(); i++) {
            assertThat(lista.get(i).get("nombre")).isEqualTo("Parada " + (i + 1));
        }
    }

    @Entonces("su trazado tiene más vértices que paradas")
    public void su_trazado_tiene_mas_vertices_que_paradas() throws Exception {
        Map<String, Object> ruta = rutaEnRespuesta();
        @SuppressWarnings("unchecked")
        List<?> paradas = (List<?>) ruta.get("paradas");
        @SuppressWarnings("unchecked")
        List<?> trazado = (List<?>) ruta.get("trazado");
        assertThat(trazado.size()).isGreaterThan(paradas.size());
    }

    @Y("el trazado inicia y termina en el punto de origen")
    public void el_trazado_inicia_y_termina_en_el_origen() {
        Boolean cerrado = jdbc.queryForObject(
                "SELECT ST_IsClosed(trazado) FROM rutas WHERE id = ?",
                Boolean.class, rutaIdConsultada);
        assertThat(cerrado).isTrue();

        Map<String, Object> extremos = jdbc.queryForMap("""
                SELECT ST_X(ST_StartPoint(trazado)) AS lon_ini,
                       ST_Y(ST_StartPoint(trazado)) AS lat_ini,
                       ST_X(ST_EndPoint(trazado))   AS lon_fin,
                       ST_Y(ST_EndPoint(trazado))   AS lat_fin
                  FROM rutas WHERE id = ?
                """, rutaIdConsultada);
        assertThat(extremos.get("lon_ini")).isEqualTo(extremos.get("lon_fin"));
        assertThat(extremos.get("lat_ini")).isEqualTo(extremos.get("lat_fin"));
    }

    @Y("no existe una novena parada duplicada para cerrar el circuito")
    public void no_existe_novena_parada_duplicada() {
        Integer novenas = jdbc.queryForObject("""
                SELECT count(*) FROM paradas
                 WHERE ruta_id = ? AND (orden = 9 OR nombre = 'Parada 9')
                """, Integer.class, rutaIdConsultada);
        assertThat(novenas).isZero();
        Integer cuantas = jdbc.queryForObject(
                "SELECT count(*) FROM paradas WHERE ruta_id = ?", Integer.class, rutaIdConsultada);
        assertThat(cuantas).isEqualTo(8);
    }

    @Entonces("las latitudes y longitudes corresponden a Jalapa")
    public void las_latitudes_y_longitudes_corresponden_a_jalapa() {
        Integer invertidas = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon FROM paradas
                    UNION ALL
                    SELECT ST_Y(geom), ST_X(geom)
                      FROM rutas, LATERAL ST_DumpPoints(trazado) AS dp(path, geom)
                ) pts
                 WHERE lat NOT BETWEEN 14.0 AND 15.0
                    OR lon NOT BETWEEN -91.0 AND -89.0
                """, Integer.class);
        assertThat(invertidas).isZero();
    }

    @Y("las geometrías utilizan el SRID 4326")
    public void las_geometrias_utilizan_srid_4326() {
        Integer malas = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT ST_SRID(ubicacion) AS srid FROM paradas
                    UNION ALL
                    SELECT ST_SRID(trazado) FROM rutas
                ) g WHERE srid <> 4326
                """, Integer.class);
        assertThat(malas).isZero();
    }

    @Entonces("contiene {int} paradas ordenadas")
    public void contiene_paradas_ordenadas(int cuantas) throws Exception {
        Map<String, Object> ruta = rutaEnRespuesta();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) ruta.get("paradas");
        assertThat(lista).hasSize(cuantas);
        for (int i = 0; i < cuantas; i++) {
            assertThat(((Number) lista.get(i).get("orden")).intValue()).isEqualTo(i + 1);
            assertThat(lista.get(i).get("nombre")).isEqualTo("Parada " + (i + 1));
        }
    }

    @Y("su trazado parcial no se cierra artificialmente")
    public void su_trazado_parcial_no_se_cierra() {
        // RUTA SECUNDARIA: tramo parcial disponible; no debe inventarse el cierre.
        Boolean cerrado = jdbc.queryForObject(
                "SELECT ST_IsClosed(trazado) FROM rutas WHERE id = ?",
                Boolean.class, rutaIdConsultada);
        assertThat(cerrado)
                .as("trazado parcial de %s debe permanecer abierto", rutaConsultada)
                .isFalse();
    }

    @Entonces("{string} está vinculada con {string}")
    public void ruta_esta_vinculada_con_bus(String nombreRuta, String identificadorBus) {
        String bus = jdbc.queryForObject("""
                SELECT v.identificador
                  FROM vehiculos v
                  JOIN rutas r ON r.id = v.ruta_id
                 WHERE r.nombre = ? AND v.activo
                """, String.class, nombreRuta);
        assertThat(bus).isEqualTo(identificadorBus);
    }

    @Entonces("aparecen {string} y {string}")
    public void aparecen_ambas_rutas(String una, String otra) throws Exception {
        List<Map<String, Object>> rutas = com.jayway.jsonpath.JsonPath.read(cuerpo(), "$");
        List<String> nombres = rutas.stream().map(r -> (String) r.get("nombre")).toList();
        assertThat(nombres).containsExactlyInAnyOrder(una, otra);
    }

    @Y("ambas incluyen nombre, trazado y paradas ordenadas")
    public void ambas_incluyen_contrato() throws Exception {
        List<Map<String, Object>> rutas = com.jayway.jsonpath.JsonPath.read(cuerpo(), "$");
        assertThat(rutas).hasSize(2);
        for (Map<String, Object> ruta : rutas) {
            assertThat(ruta.get("nombre")).isInstanceOf(String.class);
            assertThat(ruta.get("trazado")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paradas = (List<Map<String, Object>>) ruta.get("paradas");
            assertThat(paradas).isNotEmpty();
            for (int i = 0; i < paradas.size(); i++) {
                assertThat(((Number) paradas.get(i).get("orden")).intValue()).isEqualTo(i + 1);
            }
        }
    }

    private String cuerpo() throws Exception {
        return contexto.ultimaRespuesta().andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> rutaEnRespuesta() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(cuerpo(), "$");
    }
}
