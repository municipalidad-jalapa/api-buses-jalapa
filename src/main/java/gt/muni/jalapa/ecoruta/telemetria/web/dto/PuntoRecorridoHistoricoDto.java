package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import java.time.Instant;

/**
 * HU-85.
 *
 * Punto individual del recorrido historico.
 */
public record PuntoRecorridoHistoricoDto(
        double latitud,
        double longitud,
        Double velocidadKmh,
        Instant registradoEn
) {
}
