package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.NotificacionesProperties;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.EstadoAvisoProximidad;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.EstadoAvisoProximidadRepository;
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
    private final DespachadorDeAvisos despachador;

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
        List<ReservaConDistancia> vigentes = vigentes(posicion.latitud(), posicion.longitud(), posicion.rutaId());
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
        despachador.despachar(reserva.reservaId(), reserva.dispositivoId(), tipo,
                reserva.paradaId(), reserva.paradaNombre());
    }

    private EstadoAvisoProximidad nuevo(Long reservaId, TipoAviso tipo) {
        EstadoAvisoProximidad estado = new EstadoAvisoProximidad();
        estado.setReservaId(reservaId);
        estado.setTipo(tipo);
        estado.setDentro(false);
        return estado;
    }

    /**
     * Reservas vigentes con su distancia al bus. Con {@code rutaId}, solo las de
     * paradas de esa ruta: el bus de la Metroplaza pasando por el Parque Central no
     * puede avisar a quien espera en el Parque Central de la otra ruta.
     */
    private List<ReservaConDistancia> vigentes(double latitud, double longitud, Long rutaId) {
        String filtroDeRuta = rutaId != null ? "AND p.ruta_id = ?" : "";
        Object[] parametros = rutaId != null
                ? new Object[] {longitud, latitud, rutaId}
                : new Object[] {longitud, latitud};
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
                        """ + filtroDeRuta,
                (rs, i) -> new ReservaConDistancia(
                        rs.getLong("id"),
                        rs.getString("dispositivo_id"),
                        rs.getLong("parada_id"),
                        rs.getString("nombre"),
                        rs.getDouble("metros")),
                parametros);
    }

    record ReservaConDistancia(Long reservaId, String dispositivoId, Long paradaId,
                               String paradaNombre, double metros) {
    }
}
