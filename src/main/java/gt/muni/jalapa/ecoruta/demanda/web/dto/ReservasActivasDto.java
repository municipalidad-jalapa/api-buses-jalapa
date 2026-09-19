package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Las reservas vigentes por parada al momento de armar el resumen (SCRUM-284).
 *
 * <p>Trae una fila por cada parada de la ruta, incluidas las que tienen cero.
 * {@code calculadoEn} marca el instante de la consulta, en ISO-8601 UTC.
 */
@Schema(description = "Reservas vigentes por parada al momento de la consulta")
public record ReservasActivasDto(
        @Schema(description = "Una fila por parada de la ruta, en el orden del recorrido")
        List<ReservasPorParadaDto> porParada,
        @Schema(description = "Instante de calculo, ISO-8601 UTC", example = "2026-08-17T10:00:00Z")
        Instant calculadoEn) {

    /**
     * Mapea el conteo agrupado que produce el servicio. Se espera que {@code conteos}
     * ya venga en el orden del recorrido y con las paradas sin reservas en cero;
     * aqui solo se traduce a la lista del contrato.
     */
    public static ReservasActivasDto de(Map<Long, Long> conteos, Instant calculadoEn) {
        List<ReservasPorParadaDto> filas = conteos.entrySet().stream()
                .map(e -> new ReservasPorParadaDto(e.getKey(), e.getValue()))
                .toList();
        return new ReservasActivasDto(filas, calculadoEn);
    }
}
