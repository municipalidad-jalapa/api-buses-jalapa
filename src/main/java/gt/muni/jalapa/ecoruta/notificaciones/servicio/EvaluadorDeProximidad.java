package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.NotificacionesProperties;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.Aviso;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.EstadoAvisoProximidad;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.FalloDeAviso;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.DispositivoNotificacionRepository;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.EstadoAvisoProximidadRepository;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.FalloDeAvisoRepository;
import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Engancha los avisos al procesamiento de telemetria. Un fallo de FCM se
 * registra y no se propaga: la ingesta ya commitio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvaluadorDeProximidad {

    private final JdbcTemplate jdbc;
    private final NotificacionesProperties radios;
    private final EstadoAvisoProximidadRepository estados;
    private final DispositivoNotificacionRepository dispositivos;
    private final FalloDeAvisoRepository fallos;
    private final EnviadorDeNotificaciones enviador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alActualizarPosicion(PosicionVigenteActualizada evento) {
        try {
            evaluar(evento.posicion());
        } catch (RuntimeException ex) {
            log.error("La evaluacion de avisos fallo; la telemetria no se detiene", ex);
        }
    }

    void evaluar(PosicionActualResponse posicion) {
        List<ReservaConDistancia> vigentes = vigentes(posicion.latitud(), posicion.longitud());
        for (ReservaConDistancia reserva : vigentes) {
            transicionar(reserva, TipoAviso.APROXIMACION, radios.radioAproximacionMetros());
            transicionar(reserva, TipoAviso.LLEGADA, radios.radioLlegadaMetros());
        }
    }

    private void transicionar(ReservaConDistancia reserva, TipoAviso tipo, int radioMetros) {
        boolean ahoraDentro = reserva.metros() <= radioMetros;
        EstadoAvisoProximidad estado = estados
                .findByReservaIdAndTipo(reserva.reservaId(), tipo)
                .orElseGet(() -> nuevo(reserva.reservaId(), tipo));

        if (ahoraDentro && !estado.isDentro()) {
            estado.setDentro(true);
            estado.setUltimoEnvioEn(Instant.now());
            estados.save(estado);
            enviar(reserva, tipo);
        } else if (!ahoraDentro && estado.isDentro()) {
            estado.setDentro(false);
            estados.save(estado);
        }
    }

    private void enviar(ReservaConDistancia reserva, TipoAviso tipo) {
        String token = dispositivos.findById(reserva.dispositivoId())
                .map(d -> d.getToken())
                .orElse("");
        Aviso aviso = new Aviso(
                reserva.reservaId(),
                reserva.dispositivoId(),
                token,
                tipo,
                reserva.paradaId(),
                reserva.paradaNombre());
        try {
            enviador.enviar(aviso);
        } catch (RuntimeException ex) {
            registrarFallo(reserva.reservaId(), tipo, ex);
            log.warn("Aviso no enviado: tipo={} reserva={} motivo={}",
                    tipo, reserva.reservaId(), ex.getMessage());
        }
    }

    private void registrarFallo(Long reservaId, TipoAviso tipo, RuntimeException ex) {
        FalloDeAviso fallo = new FalloDeAviso();
        fallo.setReservaId(reservaId);
        fallo.setTipo(tipo);
        fallo.setDetalle(ex.getMessage());
        fallo.setOcurridoEn(Instant.now());
        fallos.save(fallo);
    }

    private EstadoAvisoProximidad nuevo(Long reservaId, TipoAviso tipo) {
        EstadoAvisoProximidad estado = new EstadoAvisoProximidad();
        estado.setReservaId(reservaId);
        estado.setTipo(tipo);
        estado.setDentro(false);
        return estado;
    }

    private List<ReservaConDistancia> vigentes(double latitud, double longitud) {
        return jdbc.query("""
                        SELECT r.id,
                               r.dispositivo_id,
                               r.parada_id,
                               p.nombre,
                               ST_Distance(
                                   p.ubicacion::geography,
                                   ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                               ) AS metros
                          FROM registros_espera r
                          JOIN paradas p ON p.id = r.parada_id
                         WHERE r.estado IN ('ACTIVA', 'RENOVADA')
                           AND r.expira_en > now()
                        """,
                (rs, i) -> new ReservaConDistancia(
                        rs.getLong("id"),
                        rs.getString("dispositivo_id"),
                        rs.getLong("parada_id"),
                        rs.getString("nombre"),
                        rs.getDouble("metros")),
                longitud, latitud);
    }

    record ReservaConDistancia(Long reservaId, String dispositivoId, Long paradaId,
                               String paradaNombre, double metros) {
    }
}
