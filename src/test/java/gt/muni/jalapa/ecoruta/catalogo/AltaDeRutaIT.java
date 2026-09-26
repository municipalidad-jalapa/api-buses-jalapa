package gt.muni.jalapa.ecoruta.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rutas nuevas desde el panel municipal: borrador, paradas, trazado y publicacion. */
class AltaDeRutaIT extends IntegracionPostgisTest {

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void borrarRutasDePrueba() {
        jdbc.update("DELETE FROM paradas WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre LIKE 'IT %')");
        jdbc.update("DELETE FROM rutas WHERE nombre LIKE 'IT %'");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void una_ruta_nueva_nace_en_borrador_y_se_publica_con_paradas_y_trazado() throws Exception {
        String creada = mockMvc.perform(post("/api/v1/admin/rutas")
                        .contentType(APPLICATION_JSON).content("{\"nombre\":\"IT Ruta Norte\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(creada).get("id").asLong();

        // El pasajero no la ve mientras es borrador.
        String publicas = mockMvc.perform(get("/api/v1/rutas")).andReturn().getResponse().getContentAsString();
        assertThat(publicas).doesNotContain("IT Ruta Norte");

        mockMvc.perform(put("/api/v1/admin/rutas/" + id + "/publicacion")
                        .contentType(APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isUnprocessableEntity());

        agregarParada(id, "IT Primera", 14.6340, -89.9810);
        mockMvc.perform(post("/api/v1/admin/rutas/" + id + "/paradas")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"IT Segunda\",\"latitud\":14.6360,\"longitud\":-89.9790}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradas[1].orden").value(2));

        mockMvc.perform(put("/api/v1/admin/rutas/" + id + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("{\"puntos\":[{\"latitud\":14.6340,\"longitud\":-89.9810},"
                                + "{\"latitud\":14.6360,\"longitud\":-89.9790}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/rutas/" + id + "/publicacion")
                        .contentType(APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sin_nombre_no_se_crea() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rutas").contentType(APPLICATION_JSON).content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CONDUCTOR")
    void el_conductor_no_crea_rutas() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rutas").contentType(APPLICATION_JSON).content("{\"nombre\":\"IT X\"}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode agregarParada(long rutaId, String nombre, double lat, double lng) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/rutas/" + rutaId + "/paradas")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"latitud\":" + lat + ",\"longitud\":" + lng + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }
}
