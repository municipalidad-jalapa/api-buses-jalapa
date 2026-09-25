package gt.muni.jalapa.ecoruta.panel.web.dto;

import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Ultima posicion conocida del bus en el tablero municipal (HU-79).
 *
 * <p>Anulable: si la ruta no tiene bus o el bus aun no reporta, el bloque
 * llega {@code null} y el resto del tablero se pinta igual. La fecha se
 * serializa en ISO-8601 UTC, como el resto del API.
 */
@Schema(description = "Ultima posicion conocida del bus; null si no hay reporte")
public record PosicionPanelDto(
        @Schema(example = "14.6335") double latitud,
        @Schema(example = "-89.9885") double longitud,
        @Schema(description = "Instante de captura, ISO-8601 UTC", example = "2026-08-17T10:00:00Z")
        Instant registradaEn) {

    /** Devuelve {@code null} cuando todavia no hay posicion que mostrar. */
    public static PosicionPanelDto de(PosicionActualResponse posicion) {
        if (posicion == null) {
            return null;
        }
        return new PosicionPanelDto(posicion.latitud(), posicion.longitud(), posicion.timestamp());
    }
}
