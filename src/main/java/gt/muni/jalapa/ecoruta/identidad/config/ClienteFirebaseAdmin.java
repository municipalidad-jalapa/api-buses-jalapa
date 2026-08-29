package gt.muni.jalapa.ecoruta.identidad.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import gt.muni.jalapa.ecoruta.identidad.FirebaseProperties;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;

import java.util.Optional;

/**
 * Acceso al SDK Admin. Si no hay credenciales, {@link #disponible()} es false
 * y quien verifique el idToken responde 401: falla cerrado.
 */
public class ClienteFirebaseAdmin implements ClienteFirebaseAuth {

    private final FirebaseApp app;
    private final FirebaseProperties propiedades;

    public ClienteFirebaseAdmin(FirebaseApp app, FirebaseProperties propiedades) {
        this.app = app;
        this.propiedades = propiedades;
    }

    @Override
    public boolean disponible() {
        return app != null;
    }

    @Override
    public FirebaseProperties propiedades() {
        return propiedades;
    }

    @Override
    public Optional<FirebaseApp> firebaseApp() {
        return Optional.ofNullable(app);
    }

    @Override
    public IdentidadFirebase verificarIdToken(String idToken) throws FirebaseAuthException {
        FirebaseToken token = FirebaseAuth.getInstance(app).verifyIdToken(idToken);
        Object aud = token.getClaims().get("aud");
        return new IdentidadFirebase(
                token.getUid(),
                token.getEmail(),
                aud == null ? "" : aud.toString());
    }
}
