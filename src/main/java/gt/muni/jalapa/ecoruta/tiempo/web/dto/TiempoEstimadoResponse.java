package gt.muni.jalapa.ecoruta.tiempo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tiempo estimado en minutos enteros (HU-72)")
public record TiempoEstimadoResponse(
        @Schema(example = "1") Long rutaId,
        @Schema(example = "55") int minutos) {
}
