package gt.muni.jalapa.ecoruta.pasajeros.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.servicio.SesionJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.VerificadorDeIdToken;
import gt.muni.jalapa.ecoruta.pasajeros.PasajeroProperties;
import gt.muni.jalapa.ecoruta.pasajeros.dominio.Pasajero;
import gt.muni.jalapa.ecoruta.pasajeros.repositorio.PasajeroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Sesion opcional del pasajero (SCRUM-26, bloque B). Mismo mecanismo que el
 * conductor y el panel: idToken de Firebase (aqui, de Google) a cambio del JWT
 * propio del backend, con rol {@code pasajero}.
 *
 * <p>No hay alta previa: cualquier cuenta de Google valida entra como pasajero.
 * El rol no da acceso a nada de operacion.
 */
@Service
@RequiredArgsConstructor
public class SesionDePasajero {

    public static final String ROL = "pasajero";

    private final VerificadorDeIdToken verificador;
    private final PasajeroRepository pasajeros;
    private final EmisorDeJwt emisor;
    private final PasajeroProperties propiedades;
    private final JdbcTemplate jdbc;

    public record Sesion(String token, Instant expiraEn, String rol, String correo) {
    }

    public record Vinculacion(int reservasVinculadas, int opinionesVinculadas) {
    }

    /** idToken invalido: BadCredentialsException (401). */
    @Transactional
    public Sesion iniciar(String idToken) {
        IdentidadFirebase identidad = verificador.verificar(idToken);
        Pasajero pasajero = pasajeros.findByFirebaseUid(identidad.uid()).orElseGet(() -> {
            Pasajero nuevo = new Pasajero();
            nuevo.setFirebaseUid(identidad.uid());
            return nuevo;
        });
        pasajero.setCorreo(identidad.correo());
        pasajero = pasajeros.save(pasajero);

        SesionJwt sesion = emisor.emitir(String.valueOf(pasajero.getId()), ROL, propiedades.sesion());
        return new Sesion(sesion.token(), sesion.expiraEn(), ROL, pasajero.getCorreo());
    }

    /**
     * Asocia a la cuenta lo que este navegador hizo como invitado. Idempotente:
     * solo toma lo que no tiene cuenta, asi que repetirla no duplica nada y lo
     * que ya pertenece a otra cuenta no se reasigna.
     */
    @Transactional
    public Vinculacion vincular(Long pasajeroId, String dispositivoId) {
        if (!StringUtils.hasText(dispositivoId) || dispositivoId.length() > 64) {
            throw new ReglaDeNegocioException("Falta el identificador del dispositivo.");
        }
        int reservas = jdbc.update("""
                UPDATE registros_espera SET pasajero_id = ?
                 WHERE dispositivo_id = ? AND pasajero_id IS NULL
                """, pasajeroId, dispositivoId);
        int opiniones = jdbc.update("""
                UPDATE opiniones SET pasajero_id = ?
                 WHERE dispositivo_id = ? AND pasajero_id IS NULL
                """, pasajeroId, dispositivoId);
        return new Vinculacion(reservas, opiniones);
    }
}
