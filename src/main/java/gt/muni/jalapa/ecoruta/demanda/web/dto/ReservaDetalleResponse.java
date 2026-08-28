package gt.muni.jalapa.ecoruta.demanda.web.dto;

import java.time.Instant;

/**
 * Una reserva vigente en la cola de la parada.
 *
 * <p>Solo {@code id} y {@code expiraEn}: el estado ya viene filtrado al
 * subconjunto vigente, y {@code paradaId} vive en el padre. Exponer mas
 * campos (dispositivo, creadoEn) arrastraria datos que este modulo no mapea.
 */
public record ReservaDetalleResponse(Long id, Instant expiraEn) {
}
