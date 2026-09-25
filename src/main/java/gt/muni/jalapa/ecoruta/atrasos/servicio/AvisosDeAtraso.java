package gt.muni.jalapa.ecoruta.atrasos.servicio;

import gt.muni.jalapa.ecoruta.atrasos.dominio.AvisoDeAtraso;
import gt.muni.jalapa.ecoruta.atrasos.dominio.MotivoDeAtraso;
import gt.muni.jalapa.ecoruta.atrasos.repositorio.AvisoDeAtrasoRepository;
import gt.muni.jalapa.ecoruta.atrasos.web.dto.AtrasoDtos.AtrasoResponse;
import gt.muni.jalapa.ecoruta.atrasos.web.dto.AtrasoDtos.ReportarAtrasoRequest;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Aviso de atraso del piloto (SCRUM-26, bloque E, criterios 2 y 3).
 *
 * <p>El piloto reporta sobre la ruta que tiene asignada (bloque D): no elige
 * ruta en la peticion, la resuelve el servidor con su cuenta. El aviso vive
 * mientras dura la demora mas un margen, y el pasajero lo ve junto al tiempo
 * estimado.
 */
@Service
@RequiredArgsConstructor
public class AvisosDeAtraso {

    /**
     * Cuanto sigue mostrandose el aviso despues de la demora estimada. El
     * atraso rara vez termina justo cuando el piloto calculo, y un aviso que
     * desaparece solo evita que quede colgado si nadie lo cancela.
     */
    static final Duration MARGEN = Duration.ofMinutes(5);

    static final String COMENTARIO_MAXIMO =
            "El comentario admite hasta 200 caracteres.";

    private final AvisoDeAtrasoRepository avisos;
    private final UsuarioRepository usuarios;
    private final VehiculoRepository vehiculos;
    private final Clock reloj;

    /**
     * Registra el aviso sobre la ruta del piloto. Un aviso nuevo reemplaza al
     * anterior: lo que vale es lo ultimo que dijo quien va manejando.
     *
     * @param conductor usuario del piloto autenticado
     */
    @Transactional
    public AtrasoResponse reportar(String conductor, ReportarAtrasoRequest peticion) {
        Long rutaId = rutaDelPiloto(conductor);
        MotivoDeAtraso motivo = peticion == null ? null : MotivoDeAtraso.de(peticion.motivo());
        if (motivo == null) {
            throw new ReglaDeNegocioException("El motivo debe ser trafico o incidente.");
        }
        Integer demora = peticion.demoraMinutos();
        if (demora == null || demora < 1 || demora > 120) {
            throw new ReglaDeNegocioException("La demora estimada va de 1 a 120 minutos.");
        }
        String comentario = StringUtils.hasText(peticion.comentario())
                ? peticion.comentario().strip() : null;
        if (comentario != null && comentario.length() > 200) {
            throw new ReglaDeNegocioException(COMENTARIO_MAXIMO);
        }

        Instant ahora = reloj.instant();
        avisos.findByRutaIdAndCanceladoEnIsNull(rutaId)
                .forEach(anterior -> anterior.cancelar(ahora));

        AvisoDeAtraso aviso = new AvisoDeAtraso();
        aviso.setRutaId(rutaId);
        aviso.setVehiculoId(vehiculos.findFirstByRutaIdAndActivoTrue(rutaId)
                .map(Vehiculo::getId).orElse(null));
        aviso.setConductor(conductor);
        aviso.setMotivo(motivo);
        aviso.setDemoraMinutos(demora);
        aviso.setComentario(comentario);
        aviso.setReportadoEn(ahora);
        aviso.setVigenteHasta(ahora.plus(Duration.ofMinutes(demora)).plus(MARGEN));
        return respuesta(avisos.save(aviso));
    }

    /** El piloto avisa que ya se normalizo: el aviso deja de mostrarse. */
    @Transactional
    public void cancelar(String conductor) {
        Long rutaId = rutaDelPiloto(conductor);
        Instant ahora = reloj.instant();
        var abiertos = avisos.findByRutaIdAndCanceladoEnIsNull(rutaId);
        if (abiertos.isEmpty()) {
            throw new ReglaDeNegocioException("No hay ningun atraso reportado en esta ruta.");
        }
        abiertos.forEach(aviso -> aviso.cancelar(ahora));
    }

    /** Lo que ve el pasajero junto al ETA; vacio si no hay atraso reportado. */
    @Transactional(readOnly = true)
    public Optional<AtrasoResponse> vigente(Long rutaId) {
        return avisos
                .findFirstByRutaIdAndCanceladoEnIsNullAndVigenteHastaAfterOrderByReportadoEnDesc(
                        rutaId, reloj.instant())
                .map(AvisosDeAtraso::respuesta);
    }

    /** El aviso vigente de la ruta del piloto, para su propia pantalla. */
    @Transactional(readOnly = true)
    public Optional<AtrasoResponse> vigenteDelPiloto(String conductor) {
        return vigente(rutaDelPiloto(conductor));
    }

    /**
     * La ruta sale de la cuenta del piloto. El sujeto del JWT es el uid de
     * Firebase en el login real y el usuario en las cuentas sembradas, asi que
     * se buscan los dos.
     */
    private Long rutaDelPiloto(String conductor) {
        Usuario piloto = usuarios.findByFirebaseUid(conductor)
                .or(() -> usuarios.findByUsername(conductor))
                .filter(Usuario::puedeIniciarSesionComoConductor)
                .orElseThrow(() -> new AccessDeniedException(
                        "Esta cuenta no es un piloto activo."));
        Long rutaId = piloto.getRutaId();
        if (rutaId == null) {
            throw new ReglaDeNegocioException("Este piloto no tiene una ruta asignada.");
        }
        return rutaId;
    }

    /** El comentario lo escribio una persona: sale neutralizado, como las opiniones. */
    private static AtrasoResponse respuesta(AvisoDeAtraso aviso) {
        return new AtrasoResponse(aviso.getId(), aviso.getRutaId(), aviso.getMotivo(),
                aviso.getDemoraMinutos(),
                aviso.getComentario() == null ? null : HtmlUtils.htmlEscape(aviso.getComentario()),
                aviso.getReportadoEn(), aviso.getVigenteHasta());
    }
}
