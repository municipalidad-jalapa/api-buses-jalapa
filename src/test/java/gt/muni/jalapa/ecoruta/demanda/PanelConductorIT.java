package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA 4.3 y 5.3: el panel del conductor se perdio en la migracion. Vuelve con
 * GET /api/v1/conductor/panel: su ruta, reservas por parada, ETA y atencion.
 */
class PanelConductorIT extends IntegracionPostgisTest {

    private static final String PANEL = "/api/v1/conductor/panel";
    private static final String UID_FIREBASE = "uid-firebase-conductor-de-prueba";

    @BeforeEach
    void limpiarPanel() {
        jdbc.update("DELETE FROM paradas_atendidas");
        jdbc.update("DELETE FROM registros_espera");
        jdbc.update("UPDATE usuarios SET firebase_uid = ? WHERE username = 'conductor1'", UID_FIREBASE);
    }

    @AfterEach
    void quitarUid() {
        jdbc.update("UPDATE usuarios SET firebase_uid = NULL WHERE username = 'conductor1'");
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void muestra_las_paradas_de_su_ruta_en_orden_con_sus_reservas() throws Exception {
        reservar(1L);
        reservar(1L);
        reservar(2L);

        mockMvc.perform(get(PANEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(1))
                .andExpect(jsonPath("$.rutaNombre").isNotEmpty())
                .andExpect(jsonPath("$.paradas[0].orden").value(1))
                .andExpect(jsonPath("$.paradas[0].reservasActivas").value(2))
                .andExpect(jsonPath("$.paradas[1].reservasActivas").value(1))
                .andExpect(jsonPath("$.paradas[*].atendidaEn", everyItem(nullValue())))
                // Sin bus reportando no se inventan minutos.
                .andExpect(jsonPath("$.estadoBus").value("SIN_DATOS"))
                .andExpect(jsonPath("$.paradas[0].minutos").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void al_marcar_una_parada_atendida_el_panel_lo_refleja_y_cierra_sus_reservas() throws Exception {
        reservar(1L);

        mockMvc.perform(post("/api/v1/rutas/1/paradas/1/atendida")).andExpect(status().isOk());

        mockMvc.perform(get(PANEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas[0].atendidaEn", notNullValue()))
                .andExpect(jsonPath("$.paradas[0].reservasActivas").value(0));
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void suma_lo_que_conto_el_piloto_hoy_y_calcula_cuantos_van_a_bordo() throws Exception {
        mockMvc.perform(get(PANEL))
                .andExpect(jsonPath("$.subieronHoy").value(0))
                .andExpect(jsonPath("$.aBordo").value(0));

        mockMvc.perform(post("/api/v1/rutas/1/paradas/1/atendida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subieron\":5,\"bajaron\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rutas/1/paradas/2/atendida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subieron\":1,\"bajaron\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PANEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subieronHoy").value(6))
                .andExpect(jsonPath("$.bajaronHoy").value(2))
                .andExpect(jsonPath("$.aBordo").value(4));
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void al_cerrar_todas_las_paradas_empieza_otra_vuelta_con_todo_pendiente() throws Exception {
        mockMvc.perform(get(PANEL)).andExpect(jsonPath("$.vuelta").value(1));

        jdbc.update("""
                INSERT INTO paradas_atendidas (ruta_id, parada_id, conductor_username, marcada_en, vuelta)
                SELECT 1, id, 'conductor1', now() - interval '5 minutes', 1 FROM paradas WHERE ruta_id = 1
                """);

        mockMvc.perform(get(PANEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vuelta").value(2))
                .andExpect(jsonPath("$.paradas[*].atendidaEn", everyItem(nullValue())));

        mockMvc.perform(post("/api/v1/rutas/1/paradas/1/atendida")).andExpect(status().isOk());

        mockMvc.perform(get(PANEL))
                .andExpect(jsonPath("$.vuelta").value(2))
                .andExpect(jsonPath("$.paradas[0].atendidaEn", notNullValue()))
                .andExpect(jsonPath("$.paradas[1].atendidaEn").value(nullValue()));
    }

    @Test
    @WithMockUser(username = UID_FIREBASE, roles = "CONDUCTOR")
    void con_la_sesion_real_el_conductor_se_reconoce_por_su_uid_de_firebase() throws Exception {
        // El JWT de jornada lleva el uid de Firebase como subject, no el username.
        mockMvc.perform(get(PANEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(1));

        mockMvc.perform(post("/api/v1/rutas/1/paradas/2/atendida"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "otro-conductor", roles = "CONDUCTOR")
    void sin_ruta_asignada_responde_403() throws Exception {
        mockMvc.perform(get(PANEL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void sin_sesion_responde_401() throws Exception {
        mockMvc.perform(get(PANEL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void trae_todas_las_paradas_de_la_ruta() throws Exception {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM paradas WHERE ruta_id = 1", Integer.class);
        mockMvc.perform(get(PANEL))
                .andExpect(jsonPath("$.paradas", hasSize(total)));
    }

    private void reservar(Long paradaId) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, 'ACTIVA', now(), now() + interval '5 minutes')
                """, UUID.randomUUID().toString(), paradaId);
    }
}
