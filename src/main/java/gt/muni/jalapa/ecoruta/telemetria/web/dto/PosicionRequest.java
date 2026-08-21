package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Una lectura de GPS.
 *
 * <p>Los nombres son latitud/longitud, en ese orden, aunque PostGIS y JTS
 * trabajen en (lon, lat): la conversion la hace {@code Geo} y solo ahi (ADR-007).
 */
public record PosicionRequest(
        @NotNull(message = "latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "latitud fuera de rango")
        @DecimalMax(value = "90.0", message = "latitud fuera de rango")
        @Schema(example = "14.6335") Double latitud,

        @NotNull(message = "longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "longitud fuera de rango")
        @DecimalMax(value = "180.0", message = "longitud fuera de rango")
        @Schema(example = "-89.9885") Double longitud,

        @Schema(example = "18") Double velocidadKmh,

        @NotNull(message = "timestamp es obligatorio")
        @Schema(description = "Reloj del dispositivo, en ISO-8601 UTC",
                example = "2026-08-17T10:00:00Z") Instant timestamp) {
}
