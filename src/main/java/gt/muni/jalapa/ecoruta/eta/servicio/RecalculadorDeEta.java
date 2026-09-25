package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * Recalcula el ETA de la ruta cuando su bus reporta una posicion nueva
 * (SCRUM-166, criterio 4). Mismo enganche que {@code EvaluadorDeProximidad}: un
 * fallo aqui se registra y no afecta la ingesta, que ya commitio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecalculadorDeEta {

    private final EtaService etas;
    private final Clock reloj;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void alActualizarPosicion(PosicionVigenteActualizada evento) {
        Long rutaId = evento.posicion().rutaId();
        if (rutaId == null) {
            return;   // bus sin ruta asignada: no hay ETA que recalcular
        }
        try {
            etas.recalcularSiCorresponde(rutaId, reloj.instant());
        } catch (RuntimeException ex) {
            log.error("El recalculo del ETA fallo; la telemetria no se detiene", ex);
        }
    }
}
