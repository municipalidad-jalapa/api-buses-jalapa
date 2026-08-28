package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;

import java.util.List;

/**
 * Demanda vigente de una parada.
 *
 * <p>{@code activas} es {@code reservas.size()} calculado aqui, no una
 * segunda consulta: asi el conteo no puede divergir de la lista. Lista
 * vacia no tiene rama especial — {@code activas=0} y {@code reservas=[]}
 * salen solos.
 */
public record ReservasDeParadaResponse(
        Long paradaId,
        int activas,
        List<ReservaDetalleResponse> reservas) {

    public static ReservasDeParadaResponse de(Long paradaId, List<Reserva> reservas) {
        List<ReservaDetalleResponse> detalle = reservas.stream()
                .map(r -> new ReservaDetalleResponse(r.getId(), r.getExpiraEn()))
                .toList();
        return new ReservasDeParadaResponse(paradaId, reservas.size(), detalle);
    }
}
