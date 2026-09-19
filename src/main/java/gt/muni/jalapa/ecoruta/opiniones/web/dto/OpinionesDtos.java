package gt.muni.jalapa.ecoruta.opiniones.web.dto;

import gt.muni.jalapa.ecoruta.opiniones.dominio.TipoOpinion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Contratos de opiniones (SCRUM-26, bloque A). */
public final class OpinionesDtos {

    private OpinionesDtos() {
    }

    @Schema(description = "Opinion del pasajero. Al menos texto o estrellas.")
    public record CrearOpinionRequest(
            @Schema(example = "comentario", allowableValues = {"queja", "comentario", "calificacion"})
            TipoOpinion tipo,
            @Schema(example = "1") Long rutaId,
            @Schema(example = "El bus paso puntual.", nullable = true) String texto,
            @Schema(example = "4", nullable = true) Integer estrellas,
            @Schema(example = "27", nullable = true) Long reservaId) {
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
            String texto,
            Instant atendidaEn,
            String atendidaPor) {
    }

    public record Promedio(
            @Schema(example = "1") Long id,
            @Schema(example = "Ruta de ejemplo - Centro de Jalapa") String nombre,
            @Schema(example = "4.2", nullable = true) Double promedio,
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
