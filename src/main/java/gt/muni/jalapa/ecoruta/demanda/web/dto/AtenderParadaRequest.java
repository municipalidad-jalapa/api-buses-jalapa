package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Lo que conto el piloto en la parada, con los botones "Subio" y "Bajo" del
 * panel. Incluye a quien subio sin avisar por la app.
 *
 * <p>El cuerpo es opcional: sin el, la parada se marca atendida con 0 y 0,
 * como antes de este conteo.
 */
public record AtenderParadaRequest(
        @Schema(example = "5") @Min(0) @Max(200) int subieron,
        @Schema(example = "2") @Min(0) @Max(200) int bajaron) {

    public static final AtenderParadaRequest SIN_CONTEO = new AtenderParadaRequest(0, 0);
}
