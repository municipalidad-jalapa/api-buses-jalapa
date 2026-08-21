package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param descartadas posiciones con el reloj fuera de la ventana de tolerancia.
 *                    El lote se acepta igual: el equipo no puede corregir su
 *                    reloj a partir de un rechazo, y reintentar seria peor
 */
@Schema(description = "Resumen del lote recibido")
public record LoteAceptadoResponse(
        @Schema(example = "3") int recibidas,
        @Schema(example = "2") int aceptadas,
        @Schema(example = "1") int descartadas) {
}
