package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;

import java.util.Map;

/**
 * Resultado interno de la orquestacion del resumen (SCRUM-283).
 *
 * <p>No es el contrato REST publico: SCRUM-284 definira los DTO finales del
 * endpoint. Este modelo existe para que la logica de composicion quede lista
 * sin adelantar la capa web.
 */
public record ResumenRutaCompuesto(
        RutaResponse ruta,
        PosicionActualResponse posicionActual,
        Map<Long, Long> reservasActivasPorParada) {
}
