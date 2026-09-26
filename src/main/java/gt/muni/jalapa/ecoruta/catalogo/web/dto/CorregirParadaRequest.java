package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nombre y ubicacion corregidos de una parada (QA 5.6). */
public record CorregirParadaRequest(
        @Schema(example = "1a Calle - Mercado")
        @NotBlank(message = "La parada necesita un nombre")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,
        @Schema(example = "14.63245")
        @NotNull @DecimalMin(value = "13.5", message = "La latitud queda fuera de Guatemala")
        @DecimalMax(value = "18.0", message = "La latitud queda fuera de Guatemala")
        Double latitud,
        @Schema(example = "-89.987308")
        @NotNull @DecimalMin(value = "-92.5", message = "La longitud queda fuera de Guatemala")
        @DecimalMax(value = "-88.0", message = "La longitud queda fuera de Guatemala")
        Double longitud) {
}
