package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AbordajeRequest(
        @NotNull(message = "subio es obligatorio")
        @Schema(example = "true") Boolean subio) {
}
