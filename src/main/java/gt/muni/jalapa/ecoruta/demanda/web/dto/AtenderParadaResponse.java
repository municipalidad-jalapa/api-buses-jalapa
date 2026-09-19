package gt.muni.jalapa.ecoruta.demanda.web.dto;

import java.time.Instant;

public record AtenderParadaResponse(
        int reservasCerradas,
        Instant marcadaEn
) {
}