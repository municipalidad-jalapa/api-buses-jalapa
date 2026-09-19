package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Respuesta de una reserva recien creada (SCRUM-306). */
public record ReservaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "1") Long paradaId,
        @Schema(example = "ACTIVA") EstadoReserva estado,
        @Schema(example = "2026-09-02T12:05:00Z") Instant expiraEn) {

    public static ReservaResponse de(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getParada().getId(),
                reserva.getEstado(),
                reserva.getExpiraEn());
    }
}
