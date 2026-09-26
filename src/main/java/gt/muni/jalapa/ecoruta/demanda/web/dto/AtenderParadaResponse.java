package gt.muni.jalapa.ecoruta.demanda.web.dto;

import java.time.Instant;

/**
 * @param reservasCerradas avisos de la parada que quedaron como abordados
 * @param marcadaEn        cuando se cerro la parada
 * @param vuelta           vuelta del dia en la que se cerro (1, 2, ...)
 */
public record AtenderParadaResponse(
        int reservasCerradas,
        Instant marcadaEn,
        int vuelta
) {
}
