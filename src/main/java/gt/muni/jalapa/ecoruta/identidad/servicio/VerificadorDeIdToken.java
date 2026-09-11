package gt.muni.jalapa.ecoruta.identidad.servicio;

/**
 * Verifica un idToken de Firebase. La implementacion de produccion usa el SDK
 * Admin; las pruebas sustituyen este puerto para no hablar con Firebase.
 */
public interface VerificadorDeIdToken {

    IdentidadFirebase verificar(String idToken);
}
