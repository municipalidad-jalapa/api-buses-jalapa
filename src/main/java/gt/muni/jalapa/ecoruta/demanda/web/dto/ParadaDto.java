package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una parada dentro del resumen de arranque (SCRUM-284).
 *
 * <p>Mismo dato que expone la consulta individual de rutas, pero como tipo
 * propio del resumen para que el contrato de esta pantalla evolucione solo.
 * Las coordenadas van con nombre, nunca como par posicional: `[14.6, -89.9]`
 * se lee al reves sin que nadie lo note (ADR-007).
 */
@Schema(description = "Parada del recorrido dentro del resumen")
public record ParadaDto(
        @Schema(example = "1") Long id,
        @Schema(example = "Parque Central") String nombre,
        @Schema(example = "14.6330") double latitud,
        @Schema(example = "-89.9890") double longitud,
        @Schema(description = "Posicion en el recorrido, desde 1", example = "1") int orden) {

    /** Mapea desde el DTO de catalogo; el ordenamiento lo garantiza {@link RutaResumenDto}. */
    public static ParadaDto de(ParadaResponse parada) {
        return new ParadaDto(
                parada.id(),
                parada.nombre(),
                parada.latitud(),
                parada.longitud(),
                parada.orden());
    }
}
