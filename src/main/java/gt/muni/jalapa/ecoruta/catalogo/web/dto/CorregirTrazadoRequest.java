package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * El trazado corregido de una ruta, en el orden del recorrido (QA 5.6).
 *
 * @param puntos de 2 a 2000 vertices, cada uno dentro de Guatemala
 */
public record CorregirTrazadoRequest(
        @Schema(description = "Vertices del recorrido, en orden")
        @NotNull @Size(min = 2, max = 2000, message = "El trazado necesita entre 2 y 2000 puntos")
        List<@Valid @NotNull PuntoRequest> puntos) {
}
