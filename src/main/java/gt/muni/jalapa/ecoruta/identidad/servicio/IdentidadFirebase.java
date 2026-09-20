package gt.muni.jalapa.ecoruta.identidad.servicio;

/**
 * Identidad ya verificada por Firebase Admin. El uid es la clave para buscar
 * el rol local; el projectId sirve para rechazar tokens de otro proyecto.
 */
public record IdentidadFirebase(String uid, String correo, String projectId) {
}
