package gt.muni.jalapa.ecoruta.eta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param minutos   null cuando la estimacion no esta disponible: no se inventa un numero
 * @param confiable true solo si se calculo con la velocidad observada del propio bus
 */
public record EtaParadaResponse(
        @Schema(example = "7") Long paradaId,
        @Schema(example = "3") int orden,
        @Schema(example = "6", nullable = true) Integer minutos,
        @Schema(example = "true") boolean confiable) {

    public static EtaParadaResponse noDisponible(Long paradaId, int orden) {
        return new EtaParadaResponse(paradaId, orden, null, false);
    }
}
