package gt.muni.jalapa.ecoruta.identidad.config;

import com.google.firebase.auth.FirebaseAuthException;
import gt.muni.jalapa.ecoruta.identidad.FirebaseProperties;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;

/**
 * Puerto sobre el SDK Admin. Las pruebas sustituyen esta interfaz; la
 * implementacion real es {@link ClienteFirebaseAdmin}.
 */
public interface ClienteFirebaseAuth {

    boolean disponible();

    FirebaseProperties propiedades();

    IdentidadFirebase verificarIdToken(String idToken) throws FirebaseAuthException;
}
