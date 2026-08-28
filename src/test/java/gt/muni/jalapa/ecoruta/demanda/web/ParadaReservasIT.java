package gt.muni.jalapa.ecoruta.demanda.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de {@code GET /api/v1/paradas/{id}/reservas}: sesion de conductor,
 * shape 200 y 404 con {@code ApiError}.
 */
class ParadaReservasIT extends IntegracionPostgisTest {

    private static final String LOGIN = "/api/v1/auth/conductor/login";
    private static final String RESERVAS = "/api/v1/paradas/{paradaId}/reservas";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sin_token_no_se_entregan_las_reservas() throws Exception {
        mockMvc.perform(get(RESERVAS, 1))
                .andExpect(noAutenticado())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/paradas/1/reservas"));
    }

    @Test
    void con_jwt_de_conductor_responde_200_solo_con_vigentes_y_el_shape_del_contrato()
            throws Exception {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Long idAbordo = insertar(1L, "ABORDO", ahora.plus(5, ChronoUnit.MINUTES));
        Long idRenovada = insertar(1L, "RENOVADA", ahora.plus(10, ChronoUnit.MINUTES));
        Long idActiva = insertar(1L, "ACTIVA", ahora.plus(20, ChronoUnit.MINUTES));
        insertar(1L, "CANCELADA", ahora.plus(30, ChronoUnit.MINUTES));
        insertar(1L, "EXPIRADA", ahora.minus(5, ChronoUnit.MINUTES));

        mockMvc.perform(get(RESERVAS, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearerConductor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradaId").value(1))
                .andExpect(jsonPath("$.activas").value(3))
                .andExpect(jsonPath("$.reservas.length()").value(3))
                .andExpect(jsonPath("$.reservas[0].id").value(idAbordo))
                .andExpect(jsonPath("$.reservas[0].expiraEn").exists())
                .andExpect(jsonPath("$.reservas[0].estado").doesNotExist())
                .andExpect(jsonPath("$.reservas[1].id").value(idRenovada))
                .andExpect(jsonPath("$.reservas[2].id").value(idActiva));
    }

    @Test
    void parada_sin_reservas_vigentes_responde_200_con_lista_vacia() throws Exception {
        mockMvc.perform(get(RESERVAS, 2)
                        .header(HttpHeaders.AUTHORIZATION, bearerConductor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradaId").value(2))
                .andExpect(jsonPath("$.activas").value(0))
                .andExpect(jsonPath("$.reservas.length()").value(0));
    }

    @Test
    void parada_inexistente_responde_404_con_ApiError() throws Exception {
        mockMvc.perform(get(RESERVAS, 999999)
                        .header(HttpHeaders.AUTHORIZATION, bearerConductor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Parada con id 999999 no existe"))
                .andExpect(jsonPath("$.path").value("/api/v1/paradas/999999/reservas"));
    }

    /**
     * Sin sesion Spring puede responder 401 o 403 segun el filtro; ambos
     * cierran el criterio "no se entrega la informacion".
     */
    private static ResultMatcher noAutenticado() {
        return result -> assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    private String bearerConductor() throws Exception {
        String cuerpo = mockMvc.perform(post(LOGIN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "conductor1", "password": "conductor123"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(cuerpo);
        return "Bearer " + json.get("token").asText();
    }

    private Long insertar(Long paradaId, String estado, Instant expiraEn) {
        return jdbc.queryForObject("""
                        INSERT INTO registros_espera (dispositivo_id, parada_id, estado, expira_en)
                        VALUES (?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                java.util.UUID.randomUUID().toString(),
                paradaId,
                estado,
                java.sql.Timestamp.from(expiraEn));
    }
}
