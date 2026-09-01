package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una reserva de lugar en una parada (Desarrollo-135). */
public record CrearReservaRequest(

        @NotBlank(message = "el dispositivo es obligatorio")
        @Size(max = 36, message = "el identificador del dispositivo no puede pasar de 36 caracteres")
        @Schema(example = "6f1c2b7e-8a3d-4e21-9c0f-2b5d7a1e4c88") String dispositivoId,

        @NotNull(message = "la parada es obligatoria")
        @Schema(example = "1") Long paradaId) {
}
