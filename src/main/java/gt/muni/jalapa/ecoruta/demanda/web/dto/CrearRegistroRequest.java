package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearRegistroRequest(
        @NotBlank(message = "dispositivoId es obligatorio")
        @Size(max = 36)
        @Schema(example = "11111111-1111-1111-1111-111111111111") String dispositivoId,

        @NotNull(message = "paradaId es obligatorio")
        @Schema(example = "1") Long paradaId) {
}
