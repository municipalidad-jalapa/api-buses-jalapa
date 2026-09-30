package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * El trazado corregido de una ruta, en el orden del recorrido (QA 5.6).
 *
 * @param puntos hasta 10000 vertices, cada uno dentro de Guatemala; sin
 *               puntos la ruta queda sin recorrido (empezar de nuevo)
 */
public record CorregirTrazadoRequest(
        @Schema(description = "Vertices del recorrido, en orden")
        @NotNull @Size(max = 10000, message = "El trazado admite hasta 10000 puntos")
        List<@Valid @NotNull PuntoRequest> puntos) {
}
