package gt.muni.jalapa.ecoruta.panel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Contratos del conteo de abordajes del panel (SCRUM-26, bloque F). */
public final class AbordajesDtos {

    private AbordajesDtos() {
    }

    /** En que tramos se agrupa el periodo consultado. */
    public enum Granularidad {
        DIA, SEMANA, MES;

        public String unidadSql() {
            return switch (this) {
                case DIA -> "day";
                case SEMANA -> "week";
                case MES -> "month";
            };
        }

        /** Texto invalido: se agrupa por dia, que es lo que mira el panel a diario. */
        public static Granularidad de(String texto) {
            if (texto == null || texto.isBlank()) {
                return DIA;
            }
            try {
                return valueOf(texto.strip().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return DIA;
            }
        }
    }

    public record ConteoPorGrupo(
            @Schema(example = "1") Long id,
            @Schema(example = "Ruta de ejemplo - Centro de Jalapa") String nombre,
            @Schema(example = "128") long abordajes) {
    }

    public record AbordajesPorPeriodo(
            @Schema(example = "2026-09-20T00:00:00Z") Instant periodo,
            @Schema(example = "37") long abordajes) {
    }

    /** @param total pasajeros marcados por el piloto en el periodo consultado */
    public record ConteoDeAbordajesResponse(
            @Schema(example = "128") long total,
            Granularidad granularidad,
            List<ConteoPorGrupo> porRuta,
            List<ConteoPorGrupo> porVehiculo,
            List<AbordajesPorPeriodo> porPeriodo) {
    }
}
