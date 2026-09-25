package gt.muni.jalapa.ecoruta.flota;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El admin del panel municipal le pone GPS a un bus sin tocar la base: crear el
 * bus con su IMEI basta para que Traccar pueda reportarlo.
 */
class GpsDesdeElPanelIT extends IntegracionPostgisTest {

    static final String IMEI = "860000000000077";

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void borrar() {
        jdbc.execute("TRUNCATE equipos RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM vehiculos WHERE identificador LIKE 'IT-%'");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_el_bus_con_su_gps_deja_lista_la_recepcion_de_traccar() throws Exception {
        long id = crear("IT-GPS1", "IT-G1", IMEI);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM dispositivos_externos d
                JOIN equipos e ON e.id = d.equipo_id
                WHERE d.identificador = ? AND e.vehiculo_id = ? AND e.estado = 'ACTIVO'
                """, Integer.class, IMEI, id)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].gps").value(IMEI));

        mockMvc.perform(post("/api/v1/integraciones/traccar/posiciones")
                        .header("X-Traccar-Token", TRACCAR)
                        .contentType(APPLICATION_JSON)
                        .content(reenvio(IMEI, Instant.now())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiar_el_gps_reemplaza_el_anterior_y_quitarlo_lo_suelta() throws Exception {
        long id = crear("IT-GPS2", "IT-G2", null);

        mockMvc.perform(put("/api/v1/admin/vehiculos/" + id + "/gps")
                        .contentType(APPLICATION_JSON).content("{\"gps\":\" " + IMEI + " \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gps").value(IMEI));
        mockMvc.perform(put("/api/v1/admin/vehiculos/" + id + "/gps")
                        .contentType(APPLICATION_JSON).content("{\"gps\":\"860000000000078\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gps").value("860000000000078"));

        // Sigue siendo el mismo equipo: no se emite uno nuevo por cambiar el GPS.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE vehiculo_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivos_externos",
                Integer.class)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/admin/vehiculos/" + id + "/gps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gps").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivos_externos",
                Integer.class)).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void un_gps_que_ya_esta_en_otro_bus_responde_422() throws Exception {
        crear("IT-GPS3", "IT-G3", IMEI);
        long otro = crear("IT-GPS4", "IT-G4", null);

        mockMvc.perform(put("/api/v1/admin/vehiculos/" + otro + "/gps")
                        .contentType(APPLICATION_JSON).content("{\"gps\":\"" + IMEI + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void un_gps_con_caracteres_raros_no_se_acepta() throws Exception {
        long id = crear("IT-GPS5", "IT-G5", null);

        mockMvc.perform(put("/api/v1/admin/vehiculos/" + id + "/gps")
                        .contentType(APPLICATION_JSON).content("{\"gps\":\"86 00; DROP\"}"))
                .andExpect(status().isBadRequest());
    }

    private long crear(String identificador, String placa, String gps) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/vehiculos")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("identificador", identificador)
                                .put("placa", placa)
                                .put("gps", gps).toString()))
                .andExpect(status().isCreated())
                .andExpect(gps == null ? jsonPath("$.gps").doesNotExist() : jsonPath("$.gps").value(gps))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
    }

    /** El cuerpo real de Traccar es {position, device}; ver RecepcionTraccarIT. */
    private static String reenvio(String uniqueId, Instant fixTime) {
        return """
                {"position":{"id":1,"deviceId":3,"latitude":14.6335,"longitude":-89.9885,
                 "speed":10,"fixTime":"%s","deviceTime":"%s","valid":true},
                 "device":{"id":3,"uniqueId":"%s"}}"""
                .formatted(fixTime, fixTime, uniqueId);
    }
}
