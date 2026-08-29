package gt.muni.jalapa.ecoruta.identidad.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El JWT de conductor es el que abre /api/v1/conductor/**. Todavia no hay
 * handlers de panel: autorizado sin mapping es 404, no 401.
 */
class AccesoPanelConductorIT extends IntegracionPostgisTest {

    @Autowired
    private EmisorDeJwt emisor;

    @Test
    void sin_token_el_panel_responde_401_con_ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/conductor/sesion"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/conductor/sesion"));
    }

    @Test
    void con_el_jwt_de_conductor_la_autorizacion_deja_pasar() throws Exception {
        String token = emisor.emitirParaConductor("uid-panel").token();

        mockMvc.perform(get("/api/v1/conductor/sesion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_jwt_inventado_no_abre_el_panel() throws Exception {
        mockMvc.perform(get("/api/v1/conductor/sesion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto.no.es.valido"))
                .andExpect(status().isUnauthorized());
    }
}
