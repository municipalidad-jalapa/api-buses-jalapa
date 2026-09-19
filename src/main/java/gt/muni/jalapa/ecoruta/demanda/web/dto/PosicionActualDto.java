package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * La ultima posicion conocida del bus dentro del resumen (SCRUM-284).
 *
 * <p>Anulable: si el bus todavia no ha reportado, este bloque llega como
 * {@code null} y la pantalla carga igual. La fecha se serializa en ISO-8601
 * UTC ({@code 2026-08-17T10:00:00Z}), como el resto del API.
 */
@Schema(description = "Ultima posicion conocida del bus; null si aun no reporta")
public record PosicionActualDto(
        @Schema(example = "14.6335") double latitud,
        @Schema(example = "-89.9885") double longitud,
        @Schema(example = "18") Double velocidadKmh,
        @Schema(description = "Instante de captura, ISO-8601 UTC", example = "2026-08-17T10:00:00Z")
        Instant capturadoEn) {

    /** Devuelve {@code null} cuando no hay posicion reportada todavia. */
    public static PosicionActualDto de(PosicionActualResponse posicion) {
        if (posicion == null) {
            return null;
        }
        return new PosicionActualDto(
                posicion.latitud(),
                posicion.longitud(),
                posicion.velocidadKmh(),
                posicion.timestamp());
    }
}
