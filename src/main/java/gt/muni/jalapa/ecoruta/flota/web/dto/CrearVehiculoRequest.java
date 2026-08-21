package gt.muni.jalapa.ecoruta.flota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearVehiculoRequest(
        @NotBlank(message = "el identificador es obligatorio")
        @Size(max = 30, message = "el identificador no puede pasar de 30 caracteres")
        @Schema(example = "BUS-02") String identificador,

        @NotBlank(message = "la placa es obligatoria")
        @Size(max = 15, message = "la placa no puede pasar de 15 caracteres")
        @Schema(example = "P-111CCC") String placa) {
}
