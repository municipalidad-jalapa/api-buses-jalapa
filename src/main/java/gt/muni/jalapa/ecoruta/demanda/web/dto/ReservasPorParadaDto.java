package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Conteo de reservas vigentes de una parada (SCRUM-284).
 *
 * <p>Cuenta solo las reservas en estado {@code ACTIVA} o {@code RENOVADA} en el
 * momento de la consulta. No hay umbrales ni objetivo: es el conteo tal cual.
 * Una parada sin reservas vigentes aparece igual, con {@code reservasActivas} en
 * cero.
 */
@Schema(description = "Reservas vigentes de una parada")
public record ReservasPorParadaDto(
        @Schema(example = "1") Long paradaId,
        @Schema(description = "Reservas en estado ACTIVA o RENOVADA", example = "3") long reservasActivas) {
}
