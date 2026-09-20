package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.servicio.ResumenRutaCompuesto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Todo lo que la pantalla principal del pasajero necesita al abrir, en una sola
 * respuesta (SCRUM-284): la ruta con sus paradas en orden, la ultima posicion
 * conocida del bus y las reservas vigentes por parada.
 *
 * <p>Es un DTO de solo lectura. El mapeo desde las piezas que orquesta
 * {@link gt.muni.jalapa.ecoruta.demanda.servicio.ResumenRutaService} vive en la
 * capa de servicio (a traves de {@link #de}), no en las entidades.
 */
@Schema(description = "Datos de arranque de la pantalla del pasajero")
public record ResumenRutaResponse(
        RutaResumenDto ruta,
        @Schema(nullable = true, description = "null si el bus aun no ha reportado posicion")
        PosicionActualDto posicionActual,
        ReservasActivasDto reservasActivas) {

    /**
     * Compone la respuesta a partir del resultado de la orquestacion.
     *
     * @param compuesto  ruta, posicion y conteos ya resueltos por el servicio
     * @param calculadoEn instante en que se hizo la consulta de demanda (ISO-8601 UTC)
     */
    public static ResumenRutaResponse de(ResumenRutaCompuesto compuesto, Instant calculadoEn) {
        return new ResumenRutaResponse(
                RutaResumenDto.de(compuesto.ruta()),
                PosicionActualDto.de(compuesto.posicionActual()),
                ReservasActivasDto.de(compuesto.reservasActivasPorParada(), calculadoEn));
    }
}
