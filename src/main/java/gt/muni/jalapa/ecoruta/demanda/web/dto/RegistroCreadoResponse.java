package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistroCreadoResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "ACTIVA") String estado) {

    public static RegistroCreadoResponse de(Reserva reserva) {
        return new RegistroCreadoResponse(reserva.getId(), reserva.getEstado().name());
    }
}
