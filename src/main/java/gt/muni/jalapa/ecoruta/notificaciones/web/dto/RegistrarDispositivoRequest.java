package gt.muni.jalapa.ecoruta.notificaciones.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarDispositivoRequest(
        @NotBlank(message = "dispositivoId es obligatorio")
        @Size(max = 36)
        @Schema(example = "11111111-1111-1111-1111-111111111111") String dispositivoId,

        @NotBlank(message = "tokenNotificacion es obligatorio")
        @Size(max = 512)
        @Schema(example = "fcm-token-de-ejemplo") String tokenNotificacion) {
}
