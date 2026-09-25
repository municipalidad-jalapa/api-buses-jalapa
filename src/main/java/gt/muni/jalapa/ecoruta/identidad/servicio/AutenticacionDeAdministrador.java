package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.PanelAdminProperties;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionAdminResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login del panel municipal (SCRUM-173). Mismo mecanismo que el conductor:
 * idToken de Firebase verificado, rol en la tabla local, JWT propio.
 *
 * <p>La diferencia con el conductor es el codigo: aqui la identidad es valida
 * pero la cuenta no tiene permiso, y eso es 403, no 401 (criterio 4).
 */
@Service
@RequiredArgsConstructor
public class AutenticacionDeAdministrador {

    static final String SIN_PERMISO = "Esta cuenta no tiene permiso para el panel municipal";

    private final VerificadorDeIdToken verificador;
    private final UsuarioRepository usuarios;
    private final EmisorDeJwt emisor;
    private final PanelAdminProperties panel;

    /** idToken invalido: BadCredentialsException (401). Sin rol ADMIN: AccessDeniedException (403). */
    @Transactional(readOnly = true)
    public SesionAdminResponse iniciarSesion(String idToken) {
        IdentidadFirebase identidad = verificador.verificar(idToken);

        Usuario usuario = usuarios.findByFirebaseUid(identidad.uid())
                .filter(Usuario::puedeEntrarAlPanelMunicipal)
                .orElseThrow(() -> new AccessDeniedException(SIN_PERMISO));

        return sesionPara(usuario);
    }

    /**
     * Sesion nueva para quien ya es admin. La llama el panel mientras hay
     * actividad; sin actividad no se renueva y el token vence solo.
     */
    @Transactional(readOnly = true)
    public SesionAdminResponse renovar(String firebaseUid) {
        Usuario usuario = usuarios.findByFirebaseUid(firebaseUid)
                .filter(Usuario::puedeEntrarAlPanelMunicipal)
                .orElseThrow(() -> new AccessDeniedException(SIN_PERMISO));
        return sesionPara(usuario);
    }

    /**
     * SCRUM-26, bloque D: el rol del token es el de la cuenta. El SuperAdmin
     * entra al mismo panel, con un token que ademas abre la administracion.
     */
    private SesionAdminResponse sesionPara(Usuario usuario) {
        String rol = usuario.getRol().administraElSistema()
                ? EmisorDeJwt.ROL_SUPERADMIN
                : EmisorDeJwt.ROL_ADMIN;
        SesionJwt sesion = emisor.emitir(usuario.getFirebaseUid(), rol, panel.inactividad());
        return new SesionAdminResponse(sesion.token(), sesion.expiraEn(), sesion.rol(),
                panel.inactividadMinutos());
    }
}
