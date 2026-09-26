package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ElegirRutaRequest(
        @NotNull(message = "la ruta es obligatoria")
        @Schema(example = "1") Long rutaId) {
}
