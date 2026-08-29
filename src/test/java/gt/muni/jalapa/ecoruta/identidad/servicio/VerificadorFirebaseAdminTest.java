package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.FirebaseProperties;
import gt.muni.jalapa.ecoruta.identidad.config.ClienteFirebaseAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificadorFirebaseAdminTest {

    @Mock
    private ClienteFirebaseAuth firebase;

    @Test
    void un_idToken_valido_del_proyecto_configurado_se_acepta() throws Exception {
        FirebaseProperties props = new FirebaseProperties("", "", "ecoruta-prod");
        when(firebase.disponible()).thenReturn(true);
        when(firebase.propiedades()).thenReturn(props);
        when(firebase.verificarIdToken("token-ok"))
                .thenReturn(new IdentidadFirebase("uid-1", "c@e.gt", "ecoruta-prod"));

        IdentidadFirebase identidad = new VerificadorFirebaseAdmin(firebase).verificar("token-ok");

        assertThat(identidad.uid()).isEqualTo("uid-1");
        assertThat(identidad.projectId()).isEqualTo("ecoruta-prod");
    }

    @Test
    void un_idToken_de_otro_proyecto_se_rechaza() throws Exception {
        FirebaseProperties props = new FirebaseProperties("", "", "ecoruta-prod");
        when(firebase.disponible()).thenReturn(true);
        when(firebase.propiedades()).thenReturn(props);
        when(firebase.verificarIdToken("token-ajeno"))
                .thenReturn(new IdentidadFirebase("uid-1", "c@e.gt", "otro-proyecto"));

        assertThatThrownBy(() -> new VerificadorFirebaseAdmin(firebase).verificar("token-ajeno"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void un_idToken_que_el_sdk_rechaza_es_401() throws Exception {
        when(firebase.disponible()).thenReturn(true);
        when(firebase.verificarIdToken("token-vencido"))
                .thenThrow(new IllegalArgumentException("expired"));

        assertThatThrownBy(() -> new VerificadorFirebaseAdmin(firebase).verificar("token-vencido"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("idToken");
    }

    @Test
    void sin_firebase_configurado_falla_cerrado() {
        when(firebase.disponible()).thenReturn(false);

        assertThatThrownBy(() -> new VerificadorFirebaseAdmin(firebase).verificar("cualquier-cosa"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
