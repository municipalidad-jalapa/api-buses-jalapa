package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Lote de posiciones. Offline-first: el equipo acumula lecturas donde no hay
 * cobertura y las manda juntas, posiblemente desordenadas (SCRUM-138).
 */
public record LotePosicionesRequest(
        @NotEmpty(message = "posiciones no puede venir vacio")
        @Valid
        @Schema(description = "De 1 a n posiciones, en cualquier orden")
        List<PosicionRequest> posiciones) {
}
