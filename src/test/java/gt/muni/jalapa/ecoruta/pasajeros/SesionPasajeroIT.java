package gt.muni.jalapa.ecoruta.pasajeros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.servicio.VerificadorDeIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-26 (HU Desarrollo-146), bloque B.2: sesion opcional del pasajero y continuidad de datos. */
class SesionPasajeroIT extends IntegracionPostgisTest {

    @MockitoBean
    private VerificadorDeIdToken verificador;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmisorDeJwt emisor;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM opiniones");
        jdbc.update("DELETE FROM pasajeros");
        when(verificador.verificar("tk-ana")).thenReturn(new IdentidadFirebase("uid-ana", "ana@gmail.com", "p"));
        when(verificador.verificar("tk-beto")).thenReturn(new IdentidadFirebase("uid-beto", "beto@gmail.com", "p"));
    }

    @Test
    void con_un_idToken_de_google_valido_devuelve_la_sesion_propia_con_rol_pasajero() throws Exception {
        String token = iniciar("tk-ana");

        assertThat(emisor.leer(token, "pasajero")).isPresent();
        assertThat(jdbc.queryForObject("SELECT correo FROM pasajeros WHERE firebase_uid = 'uid-ana'", String.class))
                .isEqualTo("ana@gmail.com");
        // Volver a entrar no crea otra cuenta.
        iniciar("tk-ana");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pasajeros", Integer.class)).isEqualTo(1);
    }

    @Test
    void un_idToken_invalido_responde_401() throws Exception {
        when(verificador.verificar(anyString())).thenThrow(new BadCredentialsException("idToken invalido"));

        mockMvc.perform(post("/api/v1/sesion/pasajero").contentType(APPLICATION_JSON)
                        .content("{\"idToken\":\"roto\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void al_iniciar_sesion_las_reservas_y_opiniones_del_navegador_quedan_en_la_cuenta() throws Exception {
        reservaDe("nav-ana");
        reservaDe("nav-ana");
        opinionDe("nav-ana");
        reservaDe("nav-otro");
        String token = iniciar("tk-ana");

        vincular(token, "nav-ana")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasVinculadas").value(2))
                .andExpect(jsonPath("$.opinionesVinculadas").value(1));

        Long ana = jdbc.queryForObject("SELECT id FROM pasajeros WHERE firebase_uid = 'uid-ana'", Long.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_espera WHERE pasajero_id = ?", Integer.class, ana))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_espera WHERE dispositivo_id = 'nav-otro' AND pasajero_id IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void vincular_es_idempotente_y_no_reasigna_lo_de_otra_cuenta() throws Exception {
        reservaDe("nav-compartido");
        String ana = iniciar("tk-ana");
        vincular(ana, "nav-compartido").andExpect(jsonPath("$.reservasVinculadas").value(1));

        vincular(ana, "nav-compartido").andExpect(jsonPath("$.reservasVinculadas").value(0));

        String beto = iniciar("tk-beto");
        vincular(beto, "nav-compartido").andExpect(jsonPath("$.reservasVinculadas").value(0));
        assertThat(jdbc.queryForObject("""
                SELECT p.firebase_uid FROM registros_espera r JOIN pasajeros p ON p.id = r.pasajero_id
                """, String.class)).isEqualTo("uid-ana");
    }

    @Test
    void con_sesion_la_opinion_queda_en_la_cuenta_y_sin_sesion_sigue_anonima() throws Exception {
        String token = iniciar("tk-ana");
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-ana")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":5}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-invitado")
                        .contentType(APPLICATION_JSON).content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":4}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM opiniones WHERE pasajero_id IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opiniones WHERE pasajero_id IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void el_rol_pasajero_no_entra_al_panel_del_conductor_ni_al_municipal() throws Exception {
        String token = "Bearer " + iniciar("tk-ana");

        mockMvc.perform(get("/api/v1/conductor/ruta").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/opiniones").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void vincular_exige_sesion_de_pasajero() throws Exception {
        vincular(null, "nav-x").andExpect(status().isUnauthorized());
        // En esta ruta solo cuenta la sesion del pasajero: el JWT del conductor no es credencial.
        vincular(emisor.emitirParaConductor("uid-cond").token(), "nav-x").andExpect(status().isUnauthorized());
    }

    @Test
    void con_cuenta_o_sin_ella_solo_se_cancela_la_propia_reserva() throws Exception {
        Long ajena = reservaDe("nav-otro");
        String token = iniciar("tk-ana");

        mockMvc.perform(delete("/api/v1/reservas/" + ajena)
                        .header("X-Dispositivo-Id", "nav-ana")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String iniciar(String idToken) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/sesion/pasajero").contentType(APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("pasajero"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = json.readTree(cuerpo);
        return nodo.get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions vincular(String token, String dispositivo) throws Exception {
        var peticion = post("/api/v1/sesion/pasajero/vincular").contentType(APPLICATION_JSON)
                .content("{\"dispositivoId\":\"" + dispositivo + "\"}");
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(peticion);
    }

    private Long reservaDe(String dispositivo) {
        return jdbc.queryForObject("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, 1, 'CANCELADA', now(), now() + interval '5 minutes') RETURNING id
                """, Long.class, dispositivo);
    }

    private void opinionDe(String dispositivo) {
        jdbc.update("INSERT INTO opiniones (tipo, ruta_id, dispositivo_id, estrellas) VALUES ('CALIFICACION', 1, ?, 4)",
                dispositivo);
    }
}
