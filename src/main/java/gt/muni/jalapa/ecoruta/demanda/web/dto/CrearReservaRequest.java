package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Solicitud para indicar que el pasajero espera en una parada.
 *
 * <p>Tipos envolventes a proposito: un primitivo no distingue "ausente" de
 * cero/falso y Bean Validation no podria responder 400.
 */
public record CrearReservaRequest(
        @NotBlank(message = "dispositivoId es obligatorio")
        @Size(max = 36, message = "dispositivoId no puede pasar de 36 caracteres")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        String dispositivoId,

        @NotNull(message = "paradaId es obligatorio")
        @Positive(message = "paradaId debe ser positivo")
        @Schema(example = "1")
        Long paradaId,

        @NotNull(message = "latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "latitud fuera de rango")
        @DecimalMax(value = "90.0", message = "latitud fuera de rango")
        @Schema(example = "14.6335")
        Double latitud,

        @NotNull(message = "longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "longitud fuera de rango")
        @DecimalMax(value = "180.0", message = "longitud fuera de rango")
        @Schema(example = "-89.9885")
        Double longitud) {
}
