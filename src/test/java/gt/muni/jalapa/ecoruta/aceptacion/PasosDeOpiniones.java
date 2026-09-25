package gt.muni.jalapa.ecoruta.aceptacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de {@code opiniones_del_servicio.feature} (SCRUM-26, bloque A). */
public class PasosDeOpiniones {

    private static final String RUTA = "/api/v1/opiniones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmisorDeJwt emisor;

    private Long queja;

    @Before("@bloque-A")
    public void limpiar() {
        jdbc.update("DELETE FROM opiniones");
    }

    @Dado("que el navegador {string} ya envió {int} opiniones")
    public void ya_envio(String navegador, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            jdbc.update("""
                    INSERT INTO opiniones (tipo, ruta_id, dispositivo_id, estrellas) VALUES ('CALIFICACION', 1, ?, 5)
                    """, navegador);
        }
    }

    @Dado("que hay una queja y un comentario sobre la ruta {long}")
    public void queja_y_comentario(long ruta) {
        queja = insertar("QUEJA", ruta, null, "Paso tarde");
        insertar("COMENTARIO", ruta, null, "Buen servicio");
    }

    @Dado("que la ruta {long} tiene calificaciones de {int} y {int} estrellas")
    public void calificaciones(long ruta, int una, int otra) {
        insertar("CALIFICACION", ruta, una, null);
        insertar("CALIFICACION", ruta, otra, null);
    }

    @Cuando("el navegador {string} opina con {int} estrellas sobre la ruta {long}")
    public void opina_estrellas(String navegador, int estrellas, long ruta) throws Exception {
        opinar(navegador, Map.of("tipo", "calificacion", "rutaId", ruta, "estrellas", estrellas));
    }

    @Cuando("el navegador {string} envía una opinión vacía sobre la ruta {long}")
    public void opina_vacio(String navegador, long ruta) throws Exception {
        opinar(navegador, Map.of("tipo", "comentario", "rutaId", ruta));
    }

    @Cuando("el navegador {string} comenta {string} sobre la ruta {long}")
    public void comenta(String navegador, String texto, long ruta) throws Exception {
        opinar(navegador, Map.of("tipo", "comentario", "rutaId", ruta, "texto", texto));
    }

    @Cuando("el administrador lista las opiniones de tipo {string}")
    public void lista_por_tipo(String tipo) throws Exception {
        listar("?tipo=" + tipo);
    }

    @Cuando("el administrador lista las opiniones de la ruta {long}")
    public void lista_por_ruta(long ruta) throws Exception {
        listar("?rutaId=" + ruta);
    }

    @Cuando("el administrador marca la queja como atendida")
    public void marca_atendida() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(patch(RUTA + "/" + queja + "/atendida")
                .header("X-Admin-Token", IntegracionPostgisTest.ADMIN)));
    }

    @Cuando("un conductor con su sesión lista las opiniones")
    public void conductor_lista() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + emisor.emitirParaConductor("uid-conductor-bdd").token())));
    }

    @Entonces("la opinión queda atribuida al navegador {string} y al bus {string}")
    public void atribuida(String navegador, String bus) {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT o.dispositivo_id, v.identificador FROM opiniones o JOIN vehiculos v ON v.id = o.vehiculo_id
                """);
        assertThat(fila.get("dispositivo_id")).isEqualTo(navegador);
        assertThat(fila.get("identificador")).isEqualTo(bus);
    }

    @Entonces("el texto guardado es {string}")
    public void texto_guardado(String texto) {
        assertThat(jdbc.queryForObject("SELECT texto FROM opiniones", String.class)).isEqualTo(texto);
    }

    @Y("el panel municipal lo devuelve como {string}")
    public void panel_devuelve(String texto) throws Exception {
        assertThat(listar("").at("/opiniones/0/texto").asText()).isEqualTo(texto);
    }

    @Entonces("el panel muestra {int} opinión")
    public void panel_muestra(int cantidad) throws Exception {
        assertThat(respuesta().get("total").asInt()).isEqualTo(cantidad);
    }

    @Y("queda registrado quién la atendió y cuándo")
    public void registrado() {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT atendida_por, atendida_en FROM opiniones WHERE id = ?", queja);
        assertThat(fila.get("atendida_por")).isNotNull();
        assertThat(fila.get("atendida_en")).isNotNull();
    }

    // {double} con el idioma "es" leeria "4.0" como 40: se compara como texto.
    @Entonces("el promedio de la ruta es {string}")
    public void promedio(String promedio) throws Exception {
        assertThat(respuesta().at("/resumen/promedioPorRuta/0/promedio").asText()).isEqualTo(promedio);
    }

    @Entonces("la tabla de opiniones tiene los índices {string}, {string} y {string}")
    public void indices(String a, String b, String c) {
        for (String indice : new String[]{a, b, c}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = ?",
                    Integer.class, indice)).as(indice).isEqualTo(1);
        }
    }

    private void opinar(String navegador, Map<String, Object> cuerpo) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", navegador)
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(cuerpo))));
    }

    private JsonNode listar(String consulta) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get(RUTA + consulta)
                .header("X-Admin-Token", IntegracionPostgisTest.ADMIN)));
        return respuesta();
    }

    private JsonNode respuesta() throws Exception {
        return json.readTree(contexto.ultimaRespuesta().andReturn().getResponse().getContentAsString());
    }

    private Long insertar(String tipo, long ruta, Integer estrellas, String texto) {
        return jdbc.queryForObject("""
                INSERT INTO opiniones (tipo, ruta_id, vehiculo_id, dispositivo_id, texto, estrellas)
                VALUES (?, ?, (SELECT id FROM vehiculos WHERE ruta_id = ? AND activo), 'nav-bdd', ?, ?)
                RETURNING id
                """, Long.class, tipo, ruta, ruta, texto, estrellas);
    }
}
