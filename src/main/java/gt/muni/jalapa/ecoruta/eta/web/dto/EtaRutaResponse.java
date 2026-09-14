package gt.muni.jalapa.ecoruta.eta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato de GET /api/v1/rutas/{rutaId}/eta (SCRUM-166).
 *
 * @param vehiculoId null si la ruta aun no tiene bus asignado
 */
public record EtaRutaResponse(
        @Schema(example = "1") Long rutaId,
        @Schema(example = "3", nullable = true) Long vehiculoId,
        @Schema(example = "2026-09-14T15:30:00Z") Instant calculadoEn,
        List<EtaParadaResponse> paradas) {

    /** La misma respuesta con todas las paradas marcadas como no disponibles. */
    public EtaRutaResponse sinEstimacion() {
        return new EtaRutaResponse(rutaId, vehiculoId, calculadoEn, paradas.stream()
                .map(p -> EtaParadaResponse.noDisponible(p.paradaId(), p.orden()))
                .toList());
    }
}
