package gt.muni.jalapa.ecoruta.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Sesion del panel municipal. El token es propio del backend.")
public record SesionAdminResponse(
        @Schema(description = "JWT del backend. Autoriza /api/v1/admin/**")
        String token,
        @Schema(description = "Vence si no se renueva: la sesion se cierra por inactividad")
        Instant expiraEn,
        @Schema(example = "admin")
        String rol,
        @Schema(description = "Plazo de inactividad configurado, para que el panel avise antes del cierre",
                example = "30")
        int inactividadMinutos) {
}
