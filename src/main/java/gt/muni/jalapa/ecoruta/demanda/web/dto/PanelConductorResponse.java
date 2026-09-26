package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.eta.web.dto.EstadoDelBus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato de GET /api/v1/conductor/panel (HU-62, HU-75 y HU-76; QA 4.3 y 5.3).
 *
 * <p>Todo lo que el panel del conductor necesita en una sola respuesta: su
 * ruta, y por cada parada en el orden del recorrido cuanta gente espera, los
 * minutos que faltan y si ya la atendio hoy.
 *
 * @param estadoBus   situacion del bus con la que se calculo el ETA
 * @param calculadoEn cuando se armo esta respuesta
 * @param subieronHoy suma de lo que conto el piloto al cerrar paradas hoy
 * @param bajaronHoy  idem, los que bajaron
 * @param aBordo      subieron menos bajaron hoy, nunca negativo
 * @param vuelta      vuelta del dia que se muestra; atendidaEn es de esta vuelta
 */
public record PanelConductorResponse(
        @Schema(example = "1") Long rutaId,
        @Schema(example = "Ruta de ejemplo - Centro de Jalapa") String rutaNombre,
        @Schema(example = "EN_RUTA") EstadoDelBus estadoBus,
        @Schema(example = "2026-09-23T15:30:00Z") Instant calculadoEn,
        List<Parada> paradas,
        @Schema(example = "21") int subieronHoy,
        @Schema(example = "9") int bajaronHoy,
        @Schema(example = "12") int aBordo,
        @Schema(example = "2") int vuelta) {

    /**
     * @param reservasActivas personas esperando (reservas ACTIVA o RENOVADA)
     * @param minutos         null si no hay estimacion confiable que mostrar
     * @param confiable       true solo con la velocidad observada del bus
     * @param atendidaEn      cuando la cerro en esta vuelta; null si sigue pendiente
     */
    public record Parada(
            @Schema(example = "3") Long paradaId,
            @Schema(example = "1a Calle - Mercado") String nombre,
            @Schema(example = "2") int orden,
            @Schema(example = "4") int reservasActivas,
            @Schema(example = "6", nullable = true) Integer minutos,
            @Schema(example = "true") boolean confiable,
            @Schema(nullable = true) Instant atendidaEn) {
    }
}
