package gt.muni.jalapa.ecoruta.flota.web;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints de administracion de equipos (SCRUM-142). */
class AdminDeEquiposIT extends IntegracionPostgisTest {

    @Test
    void sin_token_de_admin_no_se_puede_emitir_una_credencial() throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": 1, "etiqueta": "Tableta"}"""))
                .andExpect(status().isUnauthorized())
                // Mismo formato ApiError que el resto de la API (SCRUM-114), pese a
                // que este 401 lo decide un filtro y no el @RestControllerAdvice.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/equipos"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void con_un_token_de_admin_equivocado_tampoco() throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos")
                        .header(AdminBootstrapFilter.CABECERA, "token-que-no-es-el-bueno-pero-largo")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": 1, "etiqueta": "Tableta"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void el_alta_devuelve_la_credencial_una_sola_vez() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/equipos")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": 1, "etiqueta": "Tableta cabina 1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credencial")
                        .value(matchesPattern("^eq_[A-Za-z0-9_-]{12}\\.[A-Za-z0-9_-]{43}$")))
                .andReturn().getResponse().getContentAsString();

        // Y el listado no la vuelve a mostrar, ni a ella ni al hash.
        String listado = mockMvc.perform(get("/api/v1/admin/equipos")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoPublico").exists())
                .andReturn().getResponse().getContentAsString();

        String credencial = cuerpo.replaceAll(".*\"credencial\":\"([^\"]+)\".*", "$1");
        String secreto = credencial.substring(credencial.indexOf('.') + 1);
        assertThat(listado)
                .doesNotContain(secreto)
                .doesNotContain("secretoHash")
                .doesNotContain("$2a$");
    }

    @Test
    void la_validacion_de_entrada_responde_400_con_el_campo_que_falta() throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("etiqueta")));
    }

    @Test
    void un_cuerpo_ilegible_responde_400_sin_devolver_lo_que_se_mando() throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN)
                        .contentType(APPLICATION_JSON)
                        .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cuerpo de la peticion malformado o ilegible"));
    }

    @Test
    void revocar_un_equipo_inexistente_responde_404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos/999999/revocacion")
                        .header(AdminBootstrapFilter.CABECERA, ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
