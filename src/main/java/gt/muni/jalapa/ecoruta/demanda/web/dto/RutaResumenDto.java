package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Comparator;
import java.util.List;

/**
 * La ruta tal como la necesita la pantalla de arranque del pasajero (SCRUM-284):
 * identidad, trazado y paradas en orden. Sin el flag {@code activa} ni otros
 * campos de administracion que esta pantalla no usa.
 */
@Schema(description = "Ruta con sus paradas, para la pantalla de arranque")
public record RutaResumenDto(
        @Schema(example = "1") Long id,
        @Schema(example = "Ruta Centro - Terminal") String nombre,
        @Schema(description = "Paradas en el orden del recorrido") List<ParadaDto> paradas,
        @Schema(description = """
                Recorrido siguiendo las calles, en orden. Vacio si la ruta aun no
                lo tiene cargado: en ese caso el mapa une las paradas con rectas.""")
        List<PuntoResponse> trazado) {

    public static RutaResumenDto de(RutaResponse ruta) {
        return new RutaResumenDto(
                ruta.id(),
                ruta.nombre(),
                ruta.paradas().stream()
                        // Se reordena por secuencia aqui tambien: con el fetch join
                        // Hibernate no garantiza el orden de la coleccion y el mapa
                        // dibuja la linea siguiendo esta lista.
                        .sorted(Comparator.comparingInt(ParadaResponse::orden))
                        .map(ParadaDto::de)
                        .toList(),
                ruta.trazado());
    }
}
