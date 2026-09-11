package gt.muni.jalapa.ecoruta.identidad.web;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.servicio.VerificadorDeIdToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de POST /api/v1/auth/conductor. Firebase se sustituye: las
 * pruebas no hablan con el proyecto real.
 */
class AuthConductorIT extends IntegracionPostgisTest {

    @MockitoBean
    private VerificadorDeIdToken verificador;

    @Autowired
    private EmisorDeJwt emisor;

    @AfterEach
    void borrarUsuariosDePrueba() {
        jdbc.update("DELETE FROM usuarios WHERE username <> 'conductor1'");
    }

    @Test
    void idToken_valido_con_rol_conductor_responde_200_con_token_propio() throws Exception {
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid)
                VALUES ('cond-fb', 'CONDUCTOR', TRUE, 'uid-ok')
                """);
        when(verificador.verificar("idToken-valido"))
                .thenReturn(new IdentidadFirebase("uid-ok", "cond@ecoruta.gt", "ecoruta"));

        String cuerpo = mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-valido"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.rol").value("conductor"))
                .andReturn().getResponse().getContentAsString();

        String token = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertThat(token).isNotEqualTo("idToken-valido");
        assertThat(emisor.leerConductor(token)).isPresent();

        Instant expiraEn = Instant.parse(
                cuerpo.replaceAll(".*\"expiraEn\":\"([^\"]+)\".*", "$1"));
        assertThat(Duration.between(Instant.now(), expiraEn))
                .isBetween(Duration.ofHours(7).plusMinutes(50), Duration.ofHours(8).plusMinutes(10));
    }

    @Test
    void idToken_invalido_o_vencido_responde_401_con_ApiError() throws Exception {
        when(verificador.verificar(anyString()))
                .thenThrow(new BadCredentialsException("idToken de Firebase invalido o vencido"));

        mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-vencido"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("idToken de Firebase invalido o vencido"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/conductor"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void idToken_valido_sin_rol_de_conductor_responde_401() throws Exception {
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid)
                VALUES ('admin-fb', 'ADMIN', TRUE, 'uid-admin')
                """);
        when(verificador.verificar("idToken-admin"))
                .thenReturn(new IdentidadFirebase("uid-admin", "admin@ecoruta.gt", "ecoruta"));

        mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-admin"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("El usuario no tiene rol de conductor"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/conductor"));
    }
}
