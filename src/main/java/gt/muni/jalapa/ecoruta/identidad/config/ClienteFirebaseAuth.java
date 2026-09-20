package gt.muni.jalapa.ecoruta.identidad.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuthException;
import gt.muni.jalapa.ecoruta.identidad.FirebaseProperties;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;

import java.util.Optional;

/**
 * Puerto sobre el SDK Admin. Las pruebas sustituyen esta interfaz; la
 * implementacion real es {@link ClienteFirebaseAdmin}.
 */
public interface ClienteFirebaseAuth {

    boolean disponible();

    FirebaseProperties propiedades();

    IdentidadFirebase verificarIdToken(String idToken) throws FirebaseAuthException;

    /** Vacio si no hay credenciales. FCM reutiliza la misma app. */
    default Optional<FirebaseApp> firebaseApp() {
        return Optional.empty();
    }
}
