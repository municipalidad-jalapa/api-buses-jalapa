package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El conductor elige la ruta que maneja al entrar al panel, o la cambia en la jornada. */
class RutaDelConductorIT extends IntegracionPostgisTest {

    private static final String RUTA = "/api/v1/conductor/ruta";

    @AfterEach
    void restaurar() {
        jdbc.update("UPDATE usuarios SET ruta_id = 1 WHERE username = 'conductor1'");
        jdbc.update("DELETE FROM rutas WHERE nombre LIKE 'IT %'");
        jdbc.update("DELETE FROM paradas_atendidas");
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void sin_ruta_ve_las_publicadas_y_al_elegir_una_el_panel_la_usa() throws Exception {
        jdbc.update("UPDATE usuarios SET ruta_id = NULL WHERE username = 'conductor1'");
        jdbc.update("INSERT INTO rutas (nombre, activa) VALUES ('IT Borrador', FALSE)");

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").doesNotExist())
                .andExpect(jsonPath("$.rutas[*].id", hasItem(2)))
                .andExpect(jsonPath("$.rutas[*].nombre", not(hasItem("IT Borrador"))));
        mockMvc.perform(get("/api/v1/conductor/panel")).andExpect(status().isForbidden());

        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(2))
                .andExpect(jsonPath("$.rutaNombre").isNotEmpty());
        mockMvc.perform(get("/api/v1/conductor/panel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(2));
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void despues_de_cambiar_no_puede_cerrar_paradas_de_la_ruta_vieja() throws Exception {
        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rutas/1/paradas/1/atendida"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void un_borrador_o_una_ruta_que_no_existe_no_se_pueden_elegir() throws Exception {
        Long borrador = jdbc.queryForObject(
                "INSERT INTO rutas (nombre, activa) VALUES ('IT Borrador', FALSE) RETURNING id", Long.class);

        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":" + borrador + "}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":999999}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "conductor-que-no-existe", roles = "CONDUCTOR")
    void una_cuenta_que_no_es_piloto_activo_no_cambia_nada() throws Exception {
        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void un_admin_no_usa_el_selector_del_conductor() throws Exception {
        mockMvc.perform(put(RUTA).contentType(APPLICATION_JSON).content("{\"rutaId\":2}"))
                .andExpect(status().isForbidden());
    }
}
