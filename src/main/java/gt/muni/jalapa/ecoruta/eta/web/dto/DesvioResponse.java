package gt.muni.jalapa.ecoruta.eta.web.dto;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * El bus salio del trazado. Sin servicio externo de rutas (criterio 7) no se
 * conocen las calles que tomara, asi que el recorrido se ARMA con lo observado:
 *
 * <ol>
 *   <li>las posiciones reales desde que dejo el trazado,</li>
 *   <li>el punto del trazado, hacia adelante, mas cercano al bus (por donde se
 *       estima que se reincorpora),</li>
 *   <li>el resto del trazado original desde ese punto.</li>
 * </ol>
 *
 * @param metrosFueraDelTrazado   distancia actual del bus al trazado
 * @param metrosHastaReincorporar estimado por calles: linea recta por el factor
 *                                de desvio configurado
 * @param reincorporacion         punto del trazado donde se estima que vuelve
 * @param recorridoEstimado       el camino completo que se usa para el ETA, para
 *                                pintarlo en lugar del trazado original
 */
@Schema(description = "Desvio del bus respecto al trazado de la ruta")
public record DesvioResponse(
        @Schema(example = "180") int metrosFueraDelTrazado,
        @Schema(example = "320") int metrosHastaReincorporar,
        PuntoResponse reincorporacion,
        List<PuntoResponse> recorridoEstimado) {
}
