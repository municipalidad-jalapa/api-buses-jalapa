package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.NotificacionesProperties;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * QA 4.1 y 4.2: avisa por push que la reserva esta por vencer, para que el
 * pasajero la renueve aunque tenga la pestana cerrada.
 *
 * <p>Una vez por vencimiento: la fila {@code (reserva, POR_VENCER)} de
 * {@code avisos_de_proximidad} guarda cuando se aviso. Si despues el pasajero
 * renueva, la nueva expiracion queda mas alla de ese envio + margen y la
 * siguiente pasada vuelve a avisar cuando corresponda.
 *
 * <p>Sin token registrado o sin Firebase, el fallo queda en
 * {@code fallos_de_aviso} como el resto de avisos y el barrido sigue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvisadorDeVencimiento {

    private final JdbcTemplate jdbc;
    private final NotificacionesProperties propiedades;
    private final DespachadorDeAvisos despachador;

    @Scheduled(fixedDelayString = "${ecoruta.notificaciones.barrido-vencimiento-segundos:20}",
            initialDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void barrer() {
        try {
            avisarLasPorVencer();
        } catch (RuntimeException e) {
            log.warn("Fallo el barrido de avisos de vencimiento; se reintenta en la siguiente pasada", e);
        }
    }

    /** Devuelve cuantas reservas se avisaron en esta pasada. */
    @Transactional
    public int avisarLasPorVencer() {
        long margen = propiedades.avisoVencimiento().toSeconds();
        List<PorVencer> pendientes = jdbc.query("""
                        SELECT r.id, r.dispositivo_id, r.parada_id, p.nombre
                          FROM registros_espera r
                          JOIN paradas p ON p.id = r.parada_id
                          LEFT JOIN avisos_de_proximidad a
                                 ON a.reserva_id = r.id AND a.tipo = 'POR_VENCER'
                         WHERE r.estado IN ('ACTIVA', 'RENOVADA')
                           AND r.expira_en > now()
                           AND r.expira_en <= now() + make_interval(secs => ?)
                           AND (a.ultimo_envio_en IS NULL
                                OR a.ultimo_envio_en < r.expira_en - make_interval(secs => ?))
                        """,
                (rs, i) -> new PorVencer(
                        rs.getLong("id"), rs.getString("dispositivo_id"),
                        rs.getLong("parada_id"), rs.getString("nombre")),
                margen, margen);

        for (PorVencer r : pendientes) {
            // Primero se marca y despues se envia: si el envio falla, no se
            // reintenta en bucle cada 20 s; el fallo queda registrado.
            jdbc.update("""
                    INSERT INTO avisos_de_proximidad (reserva_id, tipo, dentro, ultimo_envio_en)
                    VALUES (?, 'POR_VENCER', FALSE, now())
                    ON CONFLICT (reserva_id, tipo) DO UPDATE SET ultimo_envio_en = now()
                    """, r.reservaId());
            despachador.despachar(r.reservaId(), r.dispositivoId(), TipoAviso.POR_VENCER,
                    r.paradaId(), r.paradaNombre());
        }
        return pendientes.size();
    }

    private record PorVencer(Long reservaId, String dispositivoId, Long paradaId, String paradaNombre) {
    }
}
