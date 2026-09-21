package gt.muni.jalapa.ecoruta.atrasos.web.dto;

import gt.muni.jalapa.ecoruta.atrasos.dominio.MotivoDeAtraso;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Contratos del aviso de atraso (SCRUM-26, bloque E). */
public final class AtrasoDtos {

    private AtrasoDtos() {
    }

    /**
     * La ruta no viaja: la resuelve el servidor con la cuenta del piloto.
     *
     * @param motivo        "trafico" o "incidente"
     * @param demoraMinutos de 1 a 120
     * @param comentario    opcional, hasta 200 caracteres
     */
    public record ReportarAtrasoRequest(
            @Schema(example = "trafico") String motivo,
            @Schema(example = "10") Integer demoraMinutos,
            @Schema(example = "Cerrada la 1a Calle", nullable = true) String comentario) {
    }

    /** @param vigenteHasta cuando el aviso deja de mostrarse solo */
    public record AtrasoResponse(
            Long id,
            Long rutaId,
            MotivoDeAtraso motivo,
            @Schema(example = "10") int demoraMinutos,
            @Schema(nullable = true) String comentario,
            Instant reportadoEn,
            Instant vigenteHasta) {
    }
}
