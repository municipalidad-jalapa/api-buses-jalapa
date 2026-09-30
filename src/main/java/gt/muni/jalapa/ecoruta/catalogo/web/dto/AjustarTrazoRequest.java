package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Un trazo dibujado a mano en el editor de rutas, para ajustarlo a las calles.
 *
 * @param puntos el trazo en el orden en que se dibujo; el primero es el final
 *               del recorrido que ya existe, si lo hay
 */
public record AjustarTrazoRequest(
        @Schema(description = "Puntos del trazo a mano, en orden")
        @NotNull @Size(min = 2, max = 5000, message = "El trazo necesita entre 2 y 5000 puntos")
        List<@Valid @NotNull PuntoRequest> puntos) {
}
