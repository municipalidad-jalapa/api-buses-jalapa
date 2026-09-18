package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionConductorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login del conductor: verifica el idToken de Firebase, exige rol CONDUCTOR
 * en la tabla local, y emite el JWT propio de la jornada.
 */
@Service
@RequiredArgsConstructor
public class AutenticacionDeConductor {

    private final VerificadorDeIdToken verificador;
    private final UsuarioRepository usuarios;
    private final EmisorDeJwt emisor;

    @Transactional(readOnly = true)
    public SesionConductorResponse iniciarSesion(String idToken) {
        IdentidadFirebase identidad = verificador.verificar(idToken);

        Usuario usuario = usuarios.findByFirebaseUid(identidad.uid())
                .orElseThrow(AutenticacionDeConductor::sinRolDeConductor);

        if (!usuario.puedeIniciarSesionComoConductor()) {
            throw sinRolDeConductor();
        }

        SesionJwt sesion = emisor.emitirParaConductor(identidad.uid());
        return new SesionConductorResponse(sesion.token(), sesion.expiraEn(), sesion.rol());
    }

    private static BadCredentialsException sinRolDeConductor() {
        return new BadCredentialsException("El usuario no tiene rol de conductor");
    }
}
