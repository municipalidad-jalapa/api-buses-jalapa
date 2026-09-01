package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Una reserva tal como la ve el pasajero. El campo que gobierna la HU es
 * {@link #expiraEn}: el momento en que la reserva deja de estar vigente.
 */
@Schema(description = "Reserva de lugar en una parada")
public record ReservaResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "6f1c2b7e-8a3d-4e21-9c0f-2b5d7a1e4c88") String dispositivoId,
        @Schema(example = "1") Long paradaId,
        @Schema(description = "ACTIVA, RENOVADA, ABORDO, CANCELADA o EXPIRADA", example = "ACTIVA")
        String estado,
        @Schema(example = "2026-08-31T14:00:00Z") Instant creadoEn,
        @Schema(description = "Instante en que la reserva expira", example = "2026-08-31T14:05:00Z")
        Instant expiraEn) {

    public static ReservaResponse de(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getDispositivoId(),
                reserva.getParadaId(),
                reserva.getEstado().name(),
                reserva.getCreadoEn(),
                reserva.getExpiraEn());
    }
}
