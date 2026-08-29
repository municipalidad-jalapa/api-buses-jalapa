package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un vertice del trazado de la ruta.
 *
 * <p>Con nombre, nunca como par posicional: un `[14.6, -89.9]` se lee al reves
 * sin que nadie lo note, que es el error que ADR-007 manda evitar. El trazado
 * son cientos de estos, asi que es justo donde una inversion pasaria inadvertida.
 */
@Schema(description = "Punto del trazado de la ruta")
public record PuntoResponse(
        @Schema(example = "14.6349") double latitud,
        @Schema(example = "-89.9812") double longitud) {
}
