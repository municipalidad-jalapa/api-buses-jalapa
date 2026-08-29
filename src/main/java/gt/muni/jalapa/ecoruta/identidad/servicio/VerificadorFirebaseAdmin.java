package gt.muni.jalapa.ecoruta.identidad.servicio;

import com.google.firebase.auth.FirebaseAuthException;
import gt.muni.jalapa.ecoruta.identidad.config.ClienteFirebaseAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Verifica el idToken con {@code FirebaseAuth.verifyIdToken}. Rechaza tokens
 * invalidos, vencidos o de un proyecto distinto al configurado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificadorFirebaseAdmin implements VerificadorDeIdToken {

    private final ClienteFirebaseAuth firebase;

    @Override
    public IdentidadFirebase verificar(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BadCredentialsException("idToken de Firebase invalido o vencido");
        }
        if (!firebase.disponible()) {
            throw new BadCredentialsException("idToken de Firebase invalido o vencido");
        }

        IdentidadFirebase identidad;
        try {
            identidad = firebase.verificarIdToken(idToken);
        } catch (FirebaseAuthException | IllegalArgumentException ex) {
            log.warn("idToken de Firebase rechazado: {}", ex.getClass().getSimpleName());
            throw new BadCredentialsException("idToken de Firebase invalido o vencido");
        }

        if (firebase.propiedades().tieneProjectId()
                && !firebase.propiedades().projectId().equals(identidad.projectId())) {
            log.warn("idToken de Firebase no pertenece al proyecto configurado");
            throw new BadCredentialsException("idToken de Firebase invalido o vencido");
        }

        return identidad;
    }
}
