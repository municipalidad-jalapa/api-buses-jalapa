package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.common.Geo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una parada, con las coordenadas expuestas por nombre.
 *
 * <p>Nunca como un par posicional: `[14.6, -89.9]` se puede leer al reves sin
 * que nadie lo note, y ese es el error que ADR-007 manda evitar.
 */
@Schema(description = "Parada del recorrido")
public record ParadaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Parque Central") String nombre,
        @Schema(example = "14.6330") double latitud,
        @Schema(example = "-89.9890") double longitud,
        @Schema(description = "Posicion en el recorrido, desde 1", example = "1") int orden) {

    public static ParadaResponse de(Parada parada) {
        return new ParadaResponse(
                parada.getId(),
                parada.getNombre(),
                Geo.latitud(parada.getUbicacion()),
                Geo.longitud(parada.getUbicacion()),
                parada.getOrden());
    }
}
