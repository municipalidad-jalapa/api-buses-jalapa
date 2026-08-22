package gt.muni.jalapa.ecoruta.telemetria.web;

import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaProperties;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mantiene abiertas las conexiones SSE y les empuja cada posicion nueva
 * (SCRUM-140).
 *
 * <p>Vive en {@code web/} a proposito: sostener conexiones HTTP es transporte,
 * no negocio. Ninguna regla vive aqui, solo el reparto.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DifusorDePosiciones {

    /** Nombre del evento SSE; el cliente escucha con addEventListener('posicion'). */
    public static final String EVENTO = "posicion";

    /**
     * Difundir es frecuente y suscribirse es raro, asi que conviene una lista que
     * se lee sin bloqueo y solo copia al escribir.
     */
    private final List<SseEmitter> suscriptores = new CopyOnWriteArrayList<>();

    /** Alimenta el campo id: de cada evento, base para el Last-Event-ID de SCRUM-280. */
    private final AtomicLong secuencia = new AtomicLong();

    private final TelemetriaProperties propiedades;

    /**
     * Registra un suscriptor nuevo.
     *
     * <p>Los tres callbacks no son opcionales: sin ellos la lista crece sin fin y
     * se termina difundiendo a conexiones muertas.
     */
    public SseEmitter suscribir() {
        SseEmitter emisor = new SseEmitter(propiedades.sseTimeout().toMillis());

        emisor.onCompletion(() -> quitar(emisor, "cerrada por el cliente"));
        emisor.onTimeout(() -> quitar(emisor, "expiro el timeout"));
        emisor.onError(error -> quitar(emisor, "error: " + error.getMessage()));

        suscriptores.add(emisor);
        log.debug("Suscriptor nuevo al stream de posiciones. Activos: {}", suscriptores.size());
        return emisor;
    }

    /** Manda una posicion a un solo suscriptor, para el que acaba de conectarse. */
    public void enviarA(SseEmitter emisor, PosicionActualResponse posicion) {
        try {
            emisor.send(evento(posicion));
        } catch (IOException | IllegalStateException e) {
            quitar(emisor, "no se pudo enviar la posicion inicial");
        }
    }

    /**
     * Difunde a todos los suscriptores.
     *
     * <p>AFTER_COMMIT y no un @EventListener normal: si difundiera dentro de la
     * transaccion de la ingesta, el pasajero veria una posicion que todavia puede
     * hacer rollback.
     *
     * <p>Corre en el hilo de la peticion de ingesta. Con un bus y unos pocos
     * espectadores es correcto y simple; si algun dia hubiera muchos suscriptores
     * lentos habria que pasarlo a un executor, porque empezaria a frenar la
     * ingesta. Hoy seria complejidad sin caso.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void difundir(PosicionVigenteActualizada aviso) {
        SseEmitter.SseEventBuilder evento = evento(aviso.posicion());
        // Cada envio va aislado: un suscriptor con la conexion rota no puede
        // tumbar el reparto a los demas ni propagar la excepcion a la ingesta.
        suscriptores.forEach(emisor -> {
            try {
                emisor.send(evento);
            } catch (IOException | IllegalStateException e) {
                quitar(emisor, "se cayo al difundir");
            }
        });
    }

    /**
     * Latido para que el proxy inverso no de la conexion por muerta.
     *
     * <p>Nginx cierra a los 60 s de inactividad. Con el bus detenido en la
     * terminal no hay posiciones que difundir, asi que sin esto el stream moriria
     * y EventSource reconectaria en bucle.
     *
     * <p>Se manda como comentario SSE: el navegador lo ignora y no llega a
     * ningun listener del cliente.
     */
    // Se lee la propiedad directamente y no con SpEL sobre el bean: los records
    // de @ConfigurationPropertiesScan quedan registrados con un nombre calificado
    // (ecoruta.telemetria-...TelemetriaProperties), no como "telemetriaProperties",
    // asi que "#{@telemetriaProperties...}" no resuelve y la aplicacion no arranca.
    @Scheduled(fixedDelayString = "${ecoruta.telemetria.latido-segundos:25}",
            timeUnit = TimeUnit.SECONDS)
    public void latir() {
        suscriptores.forEach(emisor -> {
            try {
                emisor.send(SseEmitter.event().comment("latido"));
            } catch (IOException | IllegalStateException e) {
                quitar(emisor, "se cayo en el latido");
            }
        });
    }

    public int cuantosSuscriptores() {
        return suscriptores.size();
    }

    private SseEmitter.SseEventBuilder evento(PosicionActualResponse posicion) {
        return SseEmitter.event()
                .id(String.valueOf(secuencia.incrementAndGet()))
                .name(EVENTO)
                // retry: le dice a EventSource cuanto esperar antes de reconectar.
                .reconnectTime(propiedades.reconexion().toMillis())
                .data(posicion);
    }

    private void quitar(SseEmitter emisor, String motivo) {
        if (suscriptores.remove(emisor)) {
            log.debug("Suscriptor fuera del stream ({}). Activos: {}", motivo, suscriptores.size());
        }
    }
}
