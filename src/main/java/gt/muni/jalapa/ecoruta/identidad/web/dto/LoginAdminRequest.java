package gt.muni.jalapa.ecoruta.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "idToken emitido por Firebase Authentication en el panel municipal")
public record LoginAdminRequest(
        @NotBlank(message = "el idToken es obligatorio")
        @Schema(example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        String idToken) {
}
