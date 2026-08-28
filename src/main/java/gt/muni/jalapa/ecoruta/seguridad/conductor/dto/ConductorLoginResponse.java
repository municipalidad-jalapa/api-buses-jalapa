package gt.muni.jalapa.ecoruta.seguridad.conductor.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link gt.muni.jalapa.ecoruta.seguridad.conductor.Usuario}.
 *
 * <p>{@code toString} no incluye el JWT: un record lo imprimiria entero.
 */
@Schema(description = "JWT de conductor. Provisional hasta SCRUM-134.")
public record ConductorLoginResponse(
        @Schema(description = "Bearer a usar en Authorization. No se vuelve a emitir el mismo.")
        String token) {

    @Override
    public String toString() {
        return "ConductorLoginResponse[token=***]";
    }
}
