package gt.muni.jalapa.ecoruta.aceptacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pasos de {@code el_piloto_avisa_un_atraso.feature} (SCRUM-26, bloque E).
 *
 * <p>El criterio 1 (marcar quien abordo) ya lo resolvio SCRUM-171: aqui se
 * verifica el comportamiento que se reutiliza, no se construye otro.
 */
public class PasosDeAtrasoDelPiloto {

    /** conductor1, sembrado en V2 y asignado a la ruta 1 en V13. */
    private static final String PILOTO = "conductor1";
    private static final long RUTA = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private ObjectMapper json;

    private String token;
    private Long reservaId;
    private JsonNode eta;

    @Before("@bloque-E")
    public void limpiar() {
        jdbc.update("DELETE FROM avisos_de_atraso");
        token = "Bearer " + emisor.emitirParaConductor(PILOTO).token();
    }

    // --- criterio 1: lo que ya hace SCRUM-171 --------------------------------

    @Dado("que una reserva de la ruta del piloto está activa")
    public void reserva_activa() {
        reservaId = jdbc.queryForObject("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                SELECT 'nav-bloque-e', p.id, 'ACTIVA', now(), now() + interval '5 minutes'
                  FROM paradas p WHERE p.ruta_id = ? ORDER BY p.orden LIMIT 1
                RETURNING id
                """, Long.class, RUTA);
    }

    @Y("que el pasajero confirmó que sí abordó")
    public void el_pasajero_confirmo() throws Exception {
        mockMvc.perform(post("/api/v1/reservas/{id}/abordaje", reservaId)
                        .header("X-Dispositivo-Id", "nav-bloque-e")
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":true}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    @Cuando("el piloto registra que no abordó")
    public void el_piloto_corrige() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(
                post("/api/v1/conductor/reservas/{id}/abordaje", reservaId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":false}")));
    }

    @Entonces("la reserva queda marcada por el conductor")
    public void marcada_por_el_conductor() {
        assertThat(jdbc.queryForObject(
                "SELECT abordaje_fuente FROM registros_espera WHERE id = ?", String.class, reservaId))
                .isEqualTo("CONDUCTOR");
        assertThat(jdbc.queryForObject(
                "SELECT subio FROM registros_espera WHERE id = ?", Boolean.class, reservaId))
                .isFalse();
    }

    @Y("esa reserva queda en estado {string}")
    public void esa_reserva_queda_en_estado(String estado) {
        assertThat(jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?", String.class, reservaId))
                .isEqualTo(estado);
    }

    // --- criterios 2 y 3: el aviso de atraso ---------------------------------

    @Cuando("el piloto reporta un atraso por {string} de {int} minutos")
    @Dado("que el piloto reportó un atraso por {string} de {int} minutos")
    public void reporta_un_atraso(String motivo, int minutos) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/conductor/atrasos")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(APPLICATION_JSON)
                .content("{\"motivo\":\"%s\",\"demoraMinutos\":%d}".formatted(motivo, minutos))));
    }

    @Y("el atraso queda registrado en la ruta del piloto")
    public void el_atraso_queda_registrado() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM avisos_de_atraso WHERE ruta_id = ? AND cancelado_en IS NULL",
                Integer.class, RUTA)).isEqualTo(1);
    }

    @Cuando("el piloto retira el aviso")
    public void retira_el_aviso() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(delete("/api/v1/conductor/atrasos/vigente")
                .header(HttpHeaders.AUTHORIZATION, token)));
    }

    @Cuando("consulto el ETA de la ruta del piloto")
    public void consulto_el_eta() throws Exception {
        eta = json.readTree(mockMvc.perform(get("/api/v1/rutas/{id}/eta", RUTA))
                .andReturn().getResponse().getContentAsString());
    }

    @Entonces("el tiempo estimado viene acompañado del aviso de demora")
    public void el_eta_trae_el_aviso() {
        assertThat(eta.path("paradas").isArray()).isTrue();
        assertThat(eta.path("atraso").isObject()).isTrue();
    }

    @Y("el aviso dice el motivo {string} y {int} minutos")
    public void el_aviso_dice(String motivo, int minutos) {
        assertThat(eta.path("atraso").path("motivo").asText()).isEqualTo(motivo);
        assertThat(eta.path("atraso").path("demoraMinutos").asInt()).isEqualTo(minutos);
    }

    @Entonces("el tiempo estimado no trae ningún aviso de demora")
    public void el_eta_no_trae_aviso() {
        // Jackson serializa el campo como null: lo que importa es que no haya aviso.
        assertThat(eta.path("atraso").isObject()).isFalse();
    }
}
