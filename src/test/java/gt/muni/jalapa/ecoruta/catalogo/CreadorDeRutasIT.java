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
 * El creador de rutas del panel (informe de QA): el lapiz que se ajusta a las
 * calles, las paradas numeradas segun el recorrido, el guardado automatico que
 * puede dejar la ruta sin recorrido y eliminar rutas.
 */
class CreadorDeRutasIT extends IntegracionPostgisTest {

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void borrarRutasDePrueba() {
        jdbc.update("""
                DELETE FROM registros_espera WHERE parada_id IN
                    (SELECT p.id FROM paradas p JOIN rutas r ON r.id = p.ruta_id WHERE r.nombre LIKE 'IT %')
                """);
        jdbc.update("UPDATE vehiculos SET ruta_id = NULL WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre LIKE 'IT %')");
        jdbc.update("DELETE FROM vehiculos WHERE identificador LIKE 'IT-%'");
        jdbc.update("DELETE FROM paradas WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre LIKE 'IT %')");
        jdbc.update("DELETE FROM rutas WHERE nombre LIKE 'IT %'");
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void un_trazo_a_mano_por_el_centro_se_ajusta_a_las_calles() throws Exception {
        // Del Parque Central por la 1a Calle, dibujado con pulso de mano.
        String cuerpo = mockMvc.perform(post("/api/v1/admin/rutas/ajuste-a-calles")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[
                                  {"latitud":14.63490,"longitud":-89.98120},
                                  {"latitud":14.63440,"longitud":-89.98290},
                                  {"latitud":14.63395,"longitud":-89.98450},
                                  {"latitud":14.63330,"longitud":-89.98580},
                                  {"latitud":14.63245,"longitud":-89.98730}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ajustado").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode puntos = json.readTree(cuerpo).get("puntos");
        assertThat(puntos.size()).isGreaterThanOrEqualTo(2);
        // Empieza y termina cerca de donde se dibujo: no se va a otra parte.
        assertThat(metros(puntos.get(0), 14.63490, -89.98120)).isLessThan(150);
        assertThat(metros(puntos.get(puntos.size() - 1), 14.63245, -89.98730)).isLessThan(150);
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void lejos_de_toda_calle_el_trazo_queda_a_mano() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rutas/ajuste-a-calles")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":14.90,"longitud":-90.40},
                                           {"latitud":14.91,"longitud":-90.41}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ajustado").value(false))
                .andExpect(jsonPath("$.puntos.length()").value(2))
                .andExpect(jsonPath("$.puntos[0].latitud").value(14.90));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void las_paradas_se_numeran_segun_donde_caen_en_el_recorrido() throws Exception {
        long ruta = crearRuta("IT Ruta ordenada");
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":14.6300,"longitud":-89.9900},
                                           {"latitud":14.6300,"longitud":-89.9800}]}"""))
                .andExpect(status().isOk());

        // Se crea primero la del final y despues la del principio.
        agregarParada(ruta, "IT Final", 14.6301, -89.9810);
        mockMvc.perform(post("/api/v1/admin/rutas/" + ruta + "/paradas")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"IT Inicio\",\"latitud\":14.6301,\"longitud\":-89.9890}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradas[0].nombre").value("IT Inicio"))
                .andExpect(jsonPath("$.paradas[0].orden").value(1))
                .andExpect(jsonPath("$.paradas[1].nombre").value("IT Final"))
                .andExpect(jsonPath("$.paradas[1].orden").value(2));

        // Si el recorrido se da vuelta, el orden de las paradas tambien.
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":14.6300,"longitud":-89.9800},
                                           {"latitud":14.6300,"longitud":-89.9900}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas[0].nombre").value("IT Final"))
                .andExpect(jsonPath("$.paradas[1].nombre").value("IT Inicio"));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void en_una_ruta_publicada_corregir_el_recorrido_no_desordena_las_paradas() throws Exception {
        long ruta = crearRuta("IT Ruta publicada");
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":14.6300,"longitud":-89.9900},
                                           {"latitud":14.6300,"longitud":-89.9800}]}"""))
                .andExpect(status().isOk());
        agregarParada(ruta, "IT Inicio", 14.6301, -89.9890);
        agregarParada(ruta, "IT Final", 14.6301, -89.9810);
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/publicacion")
                        .contentType(APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk());

        // Un recorrido al reves en una ruta que ya usan los pasajeros: el orden se queda.
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":14.6300,"longitud":-89.9800},
                                           {"latitud":14.6300,"longitud":-89.9900}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas[0].nombre").value("IT Inicio"))
                .andExpect(jsonPath("$.paradas[1].nombre").value("IT Final"));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void empezar_de_nuevo_deja_el_borrador_sin_recorrido_pero_no_una_ruta_publicada() throws Exception {
        long ruta = crearRuta("IT Ruta nueva");
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON).content("{\"puntos\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trazado.length()").value(0));

        mockMvc.perform(put("/api/v1/admin/rutas/1/trazado")
                        .contentType(APPLICATION_JSON).content("{\"puntos\":[]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void eliminar_una_ruta_la_saca_de_todos_lados_y_conserva_el_historial() throws Exception {
        long ruta = crearRuta("IT Ruta DOMI");
        long parada = agregarParada(ruta, "IT Parada", 14.6301, -89.9810).get("paradas").get(0).get("id").asLong();
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, expira_en)
                VALUES ('it-disp-ruta', ?, 'ACTIVA', now() + interval '10 minutes')
                """, parada);
        jdbc.update("INSERT INTO vehiculos (identificador, placa, ruta_id) VALUES ('IT-BUS', 'IT-P', ?)", ruta);

        mockMvc.perform(delete("/api/v1/admin/rutas/" + ruta)).andExpect(status().isNoContent());

        String lista = mockMvc.perform(get("/api/v1/admin/rutas")).andReturn().getResponse().getContentAsString();
        assertThat(lista).doesNotContain("IT Ruta DOMI");
        mockMvc.perform(get("/api/v1/rutas/" + ruta)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/rutas/" + ruta + "/trazado")
                        .contentType(APPLICATION_JSON).content("{\"puntos\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/rutas/" + ruta)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT eliminada_en IS NOT NULL FROM rutas WHERE id = ?", Boolean.class, ruta))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT ruta_id FROM vehiculos WHERE identificador = 'IT-BUS'", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT estado FROM registros_espera WHERE parada_id = ?", String.class, parada))
                .isEqualTo("CANCELADA");
    }

    @Test
    @WithMockUser(roles = "CONDUCTOR")
    void el_conductor_no_elimina_rutas_ni_ajusta_trazos() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/rutas/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/rutas/ajuste-a-calles")
                        .contentType(APPLICATION_JSON)
                        .content("{\"puntos\":[{\"latitud\":14.63,\"longitud\":-89.98},{\"latitud\":14.64,\"longitud\":-89.99}]}"))
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

    private static double metros(JsonNode punto, double lat, double lng) {
        double dy = (punto.get("latitud").asDouble() - lat) * 110_540;
        double dx = (punto.get("longitud").asDouble() - lng) * 107_700;
        return Math.hypot(dx, dy);
    }
}
