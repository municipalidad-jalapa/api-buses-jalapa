package gt.muni.jalapa.ecoruta.identidad.web;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.servicio.VerificadorDeIdToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-173 (HU Desarrollo-78): entrar al panel web municipal con el mismo
 * mecanismo de seguridad del conductor. Firebase se sustituye.
 */
class AuthAdminIT extends IntegracionPostgisTest {

    @MockitoBean
    private VerificadorDeIdToken verificador;

    @Autowired
    private EmisorDeJwt emisor;

    @BeforeEach
    void cuentas() {
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid) VALUES
                 ('admin-it', 'ADMIN', TRUE, 'uid-admin'),
                 ('admin-inactivo-it', 'ADMIN', FALSE, 'uid-admin-inactivo'),
                 ('conductor-it', 'CONDUCTOR', TRUE, 'uid-conductor')
                """);
        when(verificador.verificar("tk-admin")).thenReturn(new IdentidadFirebase("uid-admin", "a@muni.gt", "p"));
        when(verificador.verificar("tk-inactivo"))
                .thenReturn(new IdentidadFirebase("uid-admin-inactivo", "i@muni.gt", "p"));
        when(verificador.verificar("tk-conductor"))
                .thenReturn(new IdentidadFirebase("uid-conductor", "c@muni.gt", "p"));
        when(verificador.verificar("tk-desconocido"))
                .thenReturn(new IdentidadFirebase("uid-sin-cuenta", "x@gmail.com", "p"));
    }

    @AfterEach
    void borrar() {
        jdbc.update("DELETE FROM usuarios WHERE username LIKE '%-it'");
    }

    @Test
    void un_administrador_entra_y_recibe_una_sesion_que_vence_por_inactividad() throws Exception {
        String cuerpo = login("tk-admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("admin"))
                .andExpect(jsonPath("$.inactividadMinutos").value(30))
                .andReturn().getResponse().getContentAsString();

        String token = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertThat(emisor.leer(token, EmisorDeJwt.ROL_ADMIN)).isPresent();
        Instant expiraEn = Instant.parse(cuerpo.replaceAll(".*\"expiraEn\":\"([^\"]+)\".*", "$1"));
        assertThat(Duration.between(Instant.now(), expiraEn))
                .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));
    }

    @Test
    void un_conductor_que_intenta_entrar_recibe_403() throws Exception {
        login("tk-conductor")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/admin"));
    }

    @Test
    void una_cuenta_sin_registro_local_o_inactiva_recibe_403() throws Exception {
        login("tk-desconocido").andExpect(status().isForbidden());
        login("tk-inactivo").andExpect(status().isForbidden());
    }

    @Test
    void un_idToken_invalido_responde_401() throws Exception {
        when(verificador.verificar(anyString())).thenThrow(new BadCredentialsException("idToken invalido"));

        login("tk-roto").andExpect(status().isUnauthorized());
    }

    @Test
    void con_la_sesion_de_admin_ve_el_servicio_completo_sin_estar_atado_a_una_ruta() throws Exception {
        String token = emisor.emitir("uid-admin", EmisorDeJwt.ROL_ADMIN, Duration.ofMinutes(30)).token();

        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                // Las dos rutas sembradas (V6 y V12), no solo una.
                .andExpect(jsonPath("$.rutas.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.rutas[0].bus.identificador").value("BUS-01"))
                .andExpect(jsonPath("$.rutas[0].estado").value("SIN_DATOS_RECIENTES"));
    }

    @Test
    void el_jwt_de_un_conductor_no_abre_el_panel() throws Exception {
        String token = emisor.emitirParaConductor("uid-conductor").token();

        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void sin_sesion_el_panel_responde_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/servicio")).andExpect(status().isUnauthorized());
    }

    @Test
    void una_sesion_vencida_por_inactividad_responde_401() throws Exception {
        String vencido = emisor.emitir("uid-admin", EmisorDeJwt.ROL_ADMIN, Duration.ofSeconds(-1)).token();

        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void con_actividad_la_sesion_se_renueva() throws Exception {
        String casiVencido = emisor.emitir("uid-admin", EmisorDeJwt.ROL_ADMIN, Duration.ofMinutes(1)).token();

        mockMvc.perform(post("/api/v1/admin/sesion/renovacion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + casiVencido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.inactividadMinutos").value(30));
    }

    private org.springframework.test.web.servlet.ResultActions login(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/admin")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\"}"));
    }
}
