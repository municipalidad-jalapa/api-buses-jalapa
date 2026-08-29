package gt.muni.jalapa.ecoruta.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Sesion de jornada del conductor. El token es propio del backend.")
public record SesionConductorResponse(
        @Schema(description = "JWT del backend. Autoriza /api/v1/conductor/**")
        String token,
        @Schema(description = "Instante en que vence la sesion (jornada laboral)")
        Instant expiraEn,
        @Schema(example = "conductor")
        String rol) {
}
