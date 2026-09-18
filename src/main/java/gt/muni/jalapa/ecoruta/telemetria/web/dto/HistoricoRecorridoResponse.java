package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-85.
 *
 * Recorrido historico de un vehiculo preparado
 * para dibujarse directamente sobre un mapa.
 */
public record HistoricoRecorridoResponse(
        Long vehiculoId,
        LocalDate fecha,
        int totalPuntos,
        List<PuntoRecorridoHistoricoDto> puntos
) {
}
