package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * @param puntos   el trazo por las calles; si no se pudo ajustar, el mismo
 *                 que llego
 * @param ajustado false si no habia calle cerca o camino posible: el editor
 *                 avisa que el tramo quedo a mano
 */
public record TrazoAjustadoResponse(
        @Schema(description = "Trazo resultante, en orden") List<PuntoResponse> puntos,
        @Schema(example = "true") boolean ajustado) {
}
