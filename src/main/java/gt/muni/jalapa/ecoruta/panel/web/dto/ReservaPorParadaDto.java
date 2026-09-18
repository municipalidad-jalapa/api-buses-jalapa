package gt.muni.jalapa.ecoruta.panel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Conteo de reservas vigentes de una parada, para el tablero (HU-79).
 *
 * <p>Solo {@code ACTIVA} o {@code RENOVADA}. Una parada sin espera aparece
 * igual, con {@code activas} en cero: el operador no debe adivinar si la
 * parada no llego o si nadie reserva ahi.
 */
@Schema(description = "Reservas vigentes de una parada")
public record ReservaPorParadaDto(
        @Schema(example = "7") Long paradaId,
        @Schema(description = "Reservas en estado ACTIVA o RENOVADA", example = "4") long activas) {
}
