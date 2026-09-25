package gt.muni.jalapa.ecoruta.panel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Una ruta activa vista desde el tablero municipal (HU-79).
 *
 * <p>{@code vehiculoId} y {@code posicion} son anulables a proposito: una
 * ruta sin bus asignado no es un error, es una fila del tablero que el
 * operador tiene que ver. {@code transmitiendo} se calcula con el umbral
 * de {@code ecoruta.panel}, no se persiste.
 */
@Schema(description = "Operacion de una ruta activa en el panel municipal")
public record PanelRutaDto(
        @Schema(example = "1") Long rutaId,
        @Schema(example = "Parque Central - El Predio") String nombre,
        @Schema(nullable = true, description = "null si la ruta no tiene bus activo") Long vehiculoId,
        @Schema(nullable = true, description = "null si no hay bus o el bus aun no reporta")
        PosicionPanelDto posicion,
        @Schema(description = "true si la ultima posicion es mas reciente que el umbral")
        boolean transmitiendo,
        List<ReservaPorParadaDto> reservasPorParada) {
}
