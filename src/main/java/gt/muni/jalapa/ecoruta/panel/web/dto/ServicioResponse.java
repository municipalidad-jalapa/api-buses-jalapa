package gt.muni.jalapa.ecoruta.panel.web.dto;

import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** El servicio completo visto desde el panel municipal (SCRUM-173). */
public record ServicioResponse(
        @Schema(example = "2026-09-14T15:30:00Z") Instant consultadoEn,
        List<RutaEnServicio> rutas) {

    public enum EstadoDelServicio {
        /** El bus reporta posiciones recientes. */
        EN_RUTA,
        /** Hay bus, pero su ultima posicion es vieja o no existe. */
        SIN_DATOS_RECIENTES,
        /** La ruta no tiene bus activo asignado. */
        SIN_BUS
    }

    /**
     * @param bus      null si la ruta no tiene bus activo
     * @param posicion ultima posicion conocida del bus; null si nunca reporto
     */
    public record RutaEnServicio(
            @Schema(example = "1") Long rutaId,
            @Schema(example = "Ruta de ejemplo - Centro de Jalapa") String nombre,
            @Schema(example = "8") int paradas,
            @Schema(nullable = true) Bus bus,
            @Schema(example = "EN_RUTA") EstadoDelServicio estado,
            @Schema(nullable = true) PosicionActualResponse posicion) {
    }

    public record Bus(
            @Schema(example = "1") Long id,
            @Schema(example = "BUS-01") String identificador,
            @Schema(example = "P-000BBB") String placa) {
    }
}
