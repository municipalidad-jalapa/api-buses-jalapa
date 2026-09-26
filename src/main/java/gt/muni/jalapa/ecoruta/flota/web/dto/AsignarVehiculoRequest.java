package gt.muni.jalapa.ecoruta.flota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Ruta y capacidad de un vehiculo. Se mandan los dos: null quita la ruta o la
 * capacidad.
 */
public record AsignarVehiculoRequest(
        @Schema(example = "1", nullable = true) Long rutaId,
        @Min(value = 1, message = "la capacidad tiene que ser mayor que 0")
        @Max(value = 300, message = "la capacidad no puede pasar de 300")
        @Schema(example = "30", nullable = true) Integer capacidad) {
}
