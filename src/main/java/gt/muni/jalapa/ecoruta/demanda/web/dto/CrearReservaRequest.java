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
 * Alta de una reserva de lugar en una parada (Desarrollo-135 / SCRUM-306).
 *
 * <p>Tipos envolventes a proposito: un primitivo no distingue "ausente" de
 * cero/falso y Bean Validation no podria responder 400.
 */
public record CrearReservaRequest(

        @NotBlank(message = "el dispositivo es obligatorio")
        @Size(max = 36, message = "el identificador del dispositivo no puede pasar de 36 caracteres")
        @Schema(example = "6f1c2b7e-8a3d-4e21-9c0f-2b5d7a1e4c88")
        String dispositivoId,

        @NotNull(message = "la parada es obligatoria")
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