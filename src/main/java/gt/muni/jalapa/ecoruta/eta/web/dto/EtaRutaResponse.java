package gt.muni.jalapa.ecoruta.eta.web.dto;

import gt.muni.jalapa.ecoruta.atrasos.web.dto.AtrasoDtos.AtrasoResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato de GET /api/v1/rutas/{rutaId}/eta (SCRUM-166).
 *
 * @param vehiculoId null si la ruta aun no tiene bus asignado
 * @param estado     situacion del bus con la que se calculo
 * @param desvio     solo cuando el bus esta fuera del trazado; si no, null
 * @param atraso     aviso del piloto (SCRUM-26, bloque E); null si no reporto
 *                   ninguno. Los minutos estimados no lo incluyen: el calculo
 *                   mide lo que hace el bus, y el aviso dice lo que el piloto
 *                   espera que pase. Se muestran juntos, no mezclados
 */
public record EtaRutaResponse(
        @Schema(example = "1") Long rutaId,
        @Schema(example = "3", nullable = true) Long vehiculoId,
        @Schema(example = "2026-09-14T15:30:00Z") Instant calculadoEn,
        @Schema(example = "EN_RUTA") EstadoDelBus estado,
        @Schema(nullable = true) DesvioResponse desvio,
        List<EtaParadaResponse> paradas,
        @Schema(nullable = true) AtrasoResponse atraso) {

    public EtaRutaResponse(Long rutaId, Long vehiculoId, Instant calculadoEn, EstadoDelBus estado,
                           DesvioResponse desvio, List<EtaParadaResponse> paradas) {
        this(rutaId, vehiculoId, calculadoEn, estado, desvio, paradas, null);
    }

    /** La misma respuesta con todas las paradas marcadas como no disponibles. */
    public EtaRutaResponse sinEstimacion() {
        return new EtaRutaResponse(rutaId, vehiculoId, calculadoEn, EstadoDelBus.SIN_DATOS, null,
                paradas.stream()
                        .map(p -> EtaParadaResponse.noDisponible(p.paradaId(), p.orden()))
                        .toList(), atraso);
    }

    /**
     * SCRUM-26, bloque E, criterio 3: el aviso del piloto viaja junto al tiempo
     * estimado. Se agrega al servir y no al calcular, para que aparezca en
     * cuanto el piloto lo reporta, sin esperar a la siguiente posicion.
     */
    public EtaRutaResponse con(AtrasoResponse aviso) {
        return new EtaRutaResponse(rutaId, vehiculoId, calculadoEn, estado, desvio, paradas, aviso);
    }
}
