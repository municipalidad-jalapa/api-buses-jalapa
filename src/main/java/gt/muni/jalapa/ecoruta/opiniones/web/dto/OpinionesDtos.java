package gt.muni.jalapa.ecoruta.opiniones.web.dto;

import gt.muni.jalapa.ecoruta.opiniones.dominio.TipoOpinion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Contratos de opiniones (SCRUM-26, bloque A). */
public final class OpinionesDtos {

    private OpinionesDtos() {
    }

    @Schema(description = """
            Opinion del pasajero. Al menos texto, la calificacion general o alguna
            de las tres valoraciones por dimension (SCRUM-26, bloque F).""")
    public record CrearOpinionRequest(
            @Schema(example = "comentario", allowableValues = {"queja", "comentario", "calificacion"})
            TipoOpinion tipo,
            @Schema(example = "1") Long rutaId,
            @Schema(example = "El bus paso puntual.", nullable = true) String texto,
            @Schema(example = "4", nullable = true) Integer estrellas,
            @Schema(example = "27", nullable = true) Long reservaId,
            @Schema(description = "Calidad del servicio, 1 a 5", example = "4", nullable = true)
            Integer calidad,
            @Schema(description = "Limpieza de la unidad, 1 a 5", example = "3", nullable = true)
            Integer limpieza,
            @Schema(description = "Conduccion prudente del piloto, 1 a 5", example = "5", nullable = true)
            Integer conduccion) {

        /** El formulario corto (solo texto o estrellas) sigue compilando igual. */
        public CrearOpinionRequest(TipoOpinion tipo, Long rutaId, String texto, Integer estrellas,
                                   Long reservaId) {
            this(tipo, rutaId, texto, estrellas, reservaId, null, null, null);
        }
    }

    /** El vehiculo lo resuelve el servidor a partir de la ruta. */
    public record OpinionCreadaResponse(
            @Schema(example = "91") Long id,
            @Schema(example = "1") Long rutaId,
            @Schema(example = "3", nullable = true) Long vehiculoId) {
    }

    /** @param texto neutralizado: el HTML que haya escrito la persona llega como texto. */
    public record OpinionResponse(
            Long id,
            Instant creadaEn,
            TipoOpinion tipo,
            Long rutaId,
            String ruta,
            Long vehiculoId,
            String vehiculo,
            Integer estrellas,
            Integer calidad,
            Integer limpieza,
            Integer conduccion,
            String texto,
            Instant atendidaEn,
            String atendidaPor) {
    }

    /**
     * Promedios de una ruta o de un vehiculo en el periodo consultado.
     *
     * <p>SCRUM-26, bloque F: cada dimension se promedia por separado, porque
     * miden cosas distintas. Un promedio en null significa que nadie puntuo esa
     * dimension: se muestra "sin datos", nunca un cero que parezca mala nota.
     *
     * @param promedio    calificacion general (la del bloque A)
     * @param calificadas cuantas opiniones traen calificacion general
     * @param opiniones   cuantas opiniones hay en total, con o sin estrellas
     */
    public record Promedio(
            @Schema(example = "1") Long id,
            @Schema(example = "Ruta de ejemplo - Centro de Jalapa") String nombre,
            @Schema(example = "4.2", nullable = true) Double promedio,
            @Schema(description = "Calidad del servicio", example = "4.0", nullable = true)
            Double calidad,
            @Schema(description = "Limpieza de la unidad", example = "3.4", nullable = true)
            Double limpieza,
            @Schema(description = "Conduccion prudente del piloto", example = "4.6", nullable = true)
            Double conduccion,
            @Schema(example = "12") long calificadas,
            @Schema(example = "15") long opiniones) {
    }

    public record Resumen(long total, List<Promedio> promedioPorRuta, List<Promedio> promedioPorVehiculo) {
    }

    public record PaginaDeOpiniones(long total, int pagina, int tamano, List<OpinionResponse> opiniones,
                                    Resumen resumen) {
    }

    public record AtendidaResponse(Long id, String atendidaPor, Instant atendidaEn) {
    }
}
