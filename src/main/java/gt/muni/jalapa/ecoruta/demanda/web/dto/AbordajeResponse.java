package gt.muni.jalapa.ecoruta.demanda.web.dto;

import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import io.swagger.v3.oas.annotations.media.Schema;

public record AbordajeResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "ABORDO") String estado) {

    public static AbordajeResponse de(Reserva reserva) {
        return new AbordajeResponse(reserva.getId(), reserva.getEstado().name());
    }
}
