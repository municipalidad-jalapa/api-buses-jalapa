package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Un punto que manda el panel. Se acota a Guatemala: un punto fuera casi
 * siempre es latitud y longitud invertidas (ADR-007), y ese error hay que
 * frenarlo antes de que llegue a la base.
 */
public record PuntoRequest(
        @Schema(example = "14.6349")
        @NotNull @DecimalMin(value = "13.5", message = "La latitud queda fuera de Guatemala")
        @DecimalMax(value = "18.0", message = "La latitud queda fuera de Guatemala")
        Double latitud,
        @Schema(example = "-89.9812")
        @NotNull @DecimalMin(value = "-92.5", message = "La longitud queda fuera de Guatemala")
        @DecimalMax(value = "-88.0", message = "La longitud queda fuera de Guatemala")
        Double longitud) {
}
