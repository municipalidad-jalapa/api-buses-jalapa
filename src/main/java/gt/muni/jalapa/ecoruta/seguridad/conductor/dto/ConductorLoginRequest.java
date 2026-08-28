package gt.muni.jalapa.ecoruta.seguridad.conductor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link gt.muni.jalapa.ecoruta.seguridad.conductor.Usuario}.
 *
 * <p>{@code toString} no incluye {@code password}: un record lo imprimiria
 * entero y bastaria un log del request para volcarlo.
 */
@Schema(description = "Credenciales del conductor. Provisional hasta SCRUM-134.")
public record ConductorLoginRequest(
        @NotBlank(message = "el usuario es obligatorio")
        @Schema(example = "conductor1") String username,

        @NotBlank(message = "la contrasena es obligatoria")
        @Schema(example = "secret") String password) {

    @Override
    public String toString() {
        return "ConductorLoginRequest[username=%s, password=***]".formatted(username);
    }
}
