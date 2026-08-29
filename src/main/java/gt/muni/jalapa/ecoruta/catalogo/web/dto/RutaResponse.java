package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import io.swagger.v3.oas.annotations.media.Schema;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Una ruta con sus paradas. Lo consume el mapa del pasajero (HU-50). */
@Schema(description = "Ruta activa con sus paradas")
public record RutaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Ruta Centro - Terminal") String nombre,
        @Schema(example = "true") boolean activa,
        @Schema(description = "Paradas en el orden del recorrido") List<ParadaResponse> paradas,
        @Schema(description = """
                Recorrido siguiendo las calles, en orden. Vacio si la ruta aun no
                lo tiene cargado: en ese caso el mapa une las paradas con rectas.""")
        List<PuntoResponse> trazado) {

    public static RutaResponse de(Ruta ruta) {
        return new RutaResponse(
                ruta.getId(),
                ruta.getNombre(),
                ruta.isActiva(),
                ruta.getParadas().stream()
                        // Se reordena aqui ademas del @OrderBy: con join fetch,
                        // Hibernate no garantiza el orden de la coleccion, y el
                        // mapa dibuja la linea siguiendo esta secuencia.
                        .sorted(Comparator.comparingInt(p -> p.getOrden()))
                        .map(ParadaResponse::de)
                        .toList(),
                trazadoDe(ruta.getTrazado()));
    }

    /**
     * El trazado es opcional (lo carga SCRUM-136), asi que null se traduce a
     * lista vacia: el cliente recorre siempre, sin comprobar nulos.
     */
    private static List<PuntoResponse> trazadoDe(LineString trazado) {
        if (trazado == null) {
            return List.of();
        }
        // JTS guarda (x, y) = (lon, lat). Invertirlo aqui pondria el bus en China.
        return Arrays.stream(trazado.getCoordinates())
                .map((Coordinate c) -> new PuntoResponse(c.getY(), c.getX()))
                .toList();
    }
}
