package gt.muni.jalapa.ecoruta.superadmin.web.dto;

import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;

/**
 * Contratos de la administracion del sistema (SCRUM-26, bloque D).
 *
 * <p>Nunca viaja una contrasena: la identidad de las personas vive en Firebase
 * y aqui solo se guarda el uid con el que se enlaza la cuenta.
 */
public final class SuperAdminDtos {

    private SuperAdminDtos() {
    }

    /**
     * @param firebaseUid sin el, la cuenta existe pero no puede iniciar sesion
     * @param rutaId      solo tiene sentido en el piloto; los demas van sin ruta
     */
    public record CrearCuentaRequest(String username, Rol rol, String firebaseUid, Long rutaId) {
    }

    /** Todos los campos son opcionales: null = no se toca. */
    public record EditarCuentaRequest(Rol rol, String firebaseUid, Long rutaId, Boolean activo) {
    }

    public record CuentaResponse(Long id, String username, Rol rol, boolean activo,
                                 String firebaseUid, Long rutaId) {

        public static CuentaResponse de(Usuario usuario) {
            return new CuentaResponse(usuario.getId(), usuario.getUsername(), usuario.getRol(),
                    usuario.isActivo(), usuario.getFirebaseUid(), usuario.getRutaId());
        }
    }

    /** @param trazado WKT del recorrido, por ejemplo {@code LINESTRING(...)}; opcional */
    public record GuardarRutaRequest(String nombre, String trazado, Boolean activa) {
    }

    public record RutaResponse(Long id, String nombre, boolean activa, int paradas) {
    }

    public record GuardarParadaRequest(String nombre, Double latitud, Double longitud,
                                       Integer orden, Long rutaId) {
    }

    public record ParadaResponse(Long id, String nombre, double latitud, double longitud,
                                 int orden, Long rutaId) {
    }
}
