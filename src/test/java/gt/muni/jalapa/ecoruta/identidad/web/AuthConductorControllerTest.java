package gt.muni.jalapa.ecoruta.identidad.web;

import gt.muni.jalapa.ecoruta.common.GlobalExceptionHandler;
import gt.muni.jalapa.ecoruta.identidad.JwtProperties;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.identidad.servicio.AutenticacionDeConductor;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.servicio.VerificadorDeIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de POST /api/v1/auth/conductor. MockMvc standalone: no levanta
 * Spring ni Docker. La cadena de seguridad se cubre en AccesoPanelConductorIT.
 */
class AuthConductorControllerTest {

    private static final String ID_TOKEN = "idToken-de-firebase";
    private static final String UID = "uid-firebase-conductor";

    private VerificadorDeIdToken verificador;
    private UsuarioRepository usuarios;
    private EmisorDeJwt emisor;
    private MockMvc mockMvc;

    @BeforeEach
    void armar() {
        verificador = mock(VerificadorDeIdToken.class);
        usuarios = mock(UsuarioRepository.class);
        emisor = new EmisorDeJwt(new JwtProperties(
                "secreto-de-pruebas-con-mas-de-32-chars!!", 480));
        AutenticacionDeConductor autenticacion =
                new AutenticacionDeConductor(verificador, usuarios, emisor);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthConductorController(autenticacion))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void idToken_valido_con_rol_conductor_responde_200() throws Exception {
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase(UID, "cond@ecoruta.gt", "ecoruta"));
        when(usuarios.findByFirebaseUid(UID)).thenReturn(Optional.of(usuario(Rol.CONDUCTOR)));

        String cuerpo = mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-de-firebase"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.rol").value("conductor"))
                .andReturn().getResponse().getContentAsString();

        String token = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertThat(token).isNotEqualTo(ID_TOKEN);
        assertThat(emisor.leerConductor(token)).isPresent();

        Instant expiraEn = Instant.parse(
                cuerpo.replaceAll(".*\"expiraEn\":\"([^\"]+)\".*", "$1"));
        assertThat(Duration.between(Instant.now(), expiraEn))
                .isBetween(Duration.ofHours(7).plusMinutes(50), Duration.ofHours(8).plusMinutes(10));
    }

    @Test
    void idToken_invalido_o_vencido_responde_401_con_ApiError() throws Exception {
        when(verificador.verificar(ID_TOKEN))
                .thenThrow(new BadCredentialsException("idToken de Firebase invalido o vencido"));

        mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-de-firebase"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("idToken de Firebase invalido o vencido"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/conductor"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void idToken_valido_sin_rol_de_conductor_responde_401() throws Exception {
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase(UID, "admin@ecoruta.gt", "ecoruta"));
        when(usuarios.findByFirebaseUid(UID)).thenReturn(Optional.of(usuario(Rol.ADMIN)));

        mockMvc.perform(post("/api/v1/auth/conductor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"idToken-de-firebase"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("El usuario no tiene rol de conductor"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/conductor"));
    }

    private static Usuario usuario(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsername("usuario-prueba");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setFirebaseUid(UID);
        return usuario;
    }
}
