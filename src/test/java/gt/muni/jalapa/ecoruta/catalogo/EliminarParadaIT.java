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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eliminar una parada desde el panel: sale del recorrido, las siguientes suben
 * un lugar y el historial que la referencia se conserva.
 */
class EliminarParadaIT extends IntegracionPostgisTest {

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void borrarRutasDePrueba() {
        jdbc.update("""
                DELETE FROM registros_espera WHERE parada_id IN
                    (SELECT p.id FROM paradas p JOIN rutas r ON r.id = p.ruta_id WHERE r.nombre LIKE 'IT %')
                """);
        jdbc.update("DELETE FROM paradas WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre LIKE 'IT %')");
        jdbc.update("DELETE FROM rutas WHERE nombre LIKE 'IT %'");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void la_parada_sale_del_recorrido_y_las_siguientes_suben_un_lugar() throws Exception {
        long ruta = crearRuta("IT Ruta con tres");
        agregarParada(ruta, "IT Uno", 14.6340, -89.9810);
        long dos = agregarParada(ruta, "IT Dos", 14.6350, -89.9800).get("paradas").get(1).get("id").asLong();
        agregarParada(ruta, "IT Tres", 14.6360, -89.9790);
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, expira_en)
                VALUES ('it-disp-parada', ?, 'ACTIVA', now() + interval '10 minutes')
                """, dos);

        mockMvc.perform(delete("/api/v1/admin/rutas/" + ruta + "/paradas/" + dos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas.length()").value(2))
                .andExpect(jsonPath("$.paradas[0].nombre").value("IT Uno"))
                .andExpect(jsonPath("$.paradas[1].nombre").value("IT Tres"))
                .andExpect(jsonPath("$.paradas[1].orden").value(2));

        // La fila se queda para el historial; la reserva vigente se cancela.
        assertThat(jdbc.queryForObject("SELECT retirada_en IS NOT NULL FROM paradas WHERE id = ?",
                Boolean.class, dos)).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM registros_espera WHERE parada_id = ?",
                String.class, dos)).isEqualTo("CANCELADA");

        // El listado del panel tampoco la muestra, y una parada nueva va al final.
        mockMvc.perform(get("/api/v1/admin/rutas"))
                .andExpect(jsonPath("$[?(@.id == " + ruta + ")].paradas.length()").value(2));
        mockMvc.perform(post("/api/v1/admin/rutas/" + ruta + "/paradas")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"IT Cuatro\",\"latitud\":14.6370,\"longitud\":-89.9780}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradas[2].orden").value(3));

        // Ya retirada: no se puede eliminar otra vez.
        mockMvc.perform(delete("/api/v1/admin/rutas/" + ruta + "/paradas/" + dos))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void una_ruta_publicada_no_se_queda_con_menos_de_dos_paradas() throws Exception {
        long ruta = crearRuta("IT Ruta publicada");
        long uno = agregarParada(ruta, "IT Uno", 14.6340, -89.9810).get("paradas").get(0).get("id").asLong();
        agregarParada(ruta, "IT Dos", 14.6360, -89.9790);
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("{\"puntos\":[{\"latitud\":14.6340,\"longitud\":-89.9810},"
                                + "{\"latitud\":14.6360,\"longitud\":-89.9790}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/publicacion")
                        .contentType(APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/rutas/" + ruta + "/paradas/" + uno))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void una_parada_de_otra_ruta_responde_404() throws Exception {
        long ruta = crearRuta("IT Ruta ajena");
        long parada = agregarParada(ruta, "IT Uno", 14.6340, -89.9810).get("paradas").get(0).get("id").asLong();
        long otra = crearRuta("IT Otra ruta");

        mockMvc.perform(delete("/api/v1/admin/rutas/" + otra + "/paradas/" + parada))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CONDUCTOR")
    void el_conductor_no_elimina_paradas() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/rutas/1/paradas/1"))
                .andExpect(status().isForbidden());
    }

    private long crearRuta(String nombre) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/rutas")
                        .contentType(APPLICATION_JSON).content("{\"nombre\":\"" + nombre + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
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
