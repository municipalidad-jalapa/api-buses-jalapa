package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;

import java.time.Instant;

public record DetalleReservaResponse(
        Long id,
        Long paradaId,
        EstadoReserva estado,
        Instant expiraEn,
        Instant abordadoEn,
        boolean pasajeroDeclaroNoAbordo,
        Instant declaracionNoAbordoEn
) {

    public static DetalleReservaResponse de(
            Reserva reserva
    ) {
        return new DetalleReservaResponse(
                reserva.getId(),
                reserva.getParada().getId(),
                reserva.getEstado(),
                reserva.getExpiraEn(),
                reserva.getAbordadoEn(),
                reserva.isPasajeroDeclaroNoAbordo(),
                reserva.getDeclaracionNoAbordoEn()
        );
    }
}