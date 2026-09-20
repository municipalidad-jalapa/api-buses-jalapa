package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.JwtProperties;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionConductorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionDeConductorTest {

    private static final String ID_TOKEN = "idToken-de-firebase";
    private static final String UID = "uid-firebase-conductor";

    @Mock
    private VerificadorDeIdToken verificador;

    @Mock
    private UsuarioRepository usuarios;

    private AutenticacionDeConductor autenticacion;
    private EmisorDeJwt emisor;

    @BeforeEach
    void armar() {
        emisor = new EmisorDeJwt(new JwtProperties(
                "secreto-de-pruebas-con-mas-de-32-chars!!", 480));
        autenticacion = new AutenticacionDeConductor(verificador, usuarios, emisor);
    }

    @Test
    void idToken_valido_con_rol_conductor_emite_jwt_propio_de_jornada() {
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase(UID, "cond@ecoruta.gt", "ecoruta"));
        when(usuarios.findByFirebaseUid(UID)).thenReturn(Optional.of(conductor(UID)));

        SesionConductorResponse sesion = autenticacion.iniciarSesion(ID_TOKEN);

        assertThat(sesion.token()).isNotBlank().isNotEqualTo(ID_TOKEN);
        assertThat(sesion.rol()).isEqualTo("conductor");
        assertThat(Duration.between(Instant.now(), sesion.expiraEn()))
                .isBetween(Duration.ofHours(7).plusMinutes(50), Duration.ofHours(8).plusMinutes(10));
        assertThat(emisor.leerConductor(sesion.token()))
                .get()
                .extracting(SesionJwt::rol, SesionJwt::subject)
                .containsExactly("conductor", UID);
    }

    @Test
    void idToken_invalido_o_vencido_es_401() {
        when(verificador.verificar(ID_TOKEN))
                .thenThrow(new BadCredentialsException("idToken de Firebase invalido o vencido"));

        assertThatThrownBy(() -> autenticacion.iniciarSesion(ID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("idToken");
    }

    @Test
    void idToken_valido_pero_sin_usuario_local_es_401() {
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase(UID, "x@y.z", "ecoruta"));
        when(usuarios.findByFirebaseUid(UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autenticacion.iniciarSesion(ID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("conductor");
    }

    @Test
    void idToken_valido_con_rol_admin_no_obtiene_sesion_de_conductor() {
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase("uid-admin", "admin@ecoruta.gt", "ecoruta"));
        when(usuarios.findByFirebaseUid("uid-admin")).thenReturn(Optional.of(admin("uid-admin")));

        assertThatThrownBy(() -> autenticacion.iniciarSesion(ID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("conductor");
    }

    @Test
    void conductor_inactivo_tampoco_obtiene_sesion() {
        Usuario inactivo = conductor(UID);
        inactivo.setActivo(false);
        when(verificador.verificar(ID_TOKEN))
                .thenReturn(new IdentidadFirebase(UID, "cond@ecoruta.gt", "ecoruta"));
        when(usuarios.findByFirebaseUid(UID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> autenticacion.iniciarSesion(ID_TOKEN))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static Usuario conductor(String firebaseUid) {
        Usuario usuario = new Usuario();
        usuario.setUsername("conductor-prueba");
        usuario.setRol(Rol.CONDUCTOR);
        usuario.setActivo(true);
        usuario.setFirebaseUid(firebaseUid);
        return usuario;
    }

    private static Usuario admin(String firebaseUid) {
        Usuario usuario = new Usuario();
        usuario.setUsername("admin-prueba");
        usuario.setRol(Rol.ADMIN);
        usuario.setActivo(true);
        usuario.setFirebaseUid(firebaseUid);
        return usuario;
    }
}
