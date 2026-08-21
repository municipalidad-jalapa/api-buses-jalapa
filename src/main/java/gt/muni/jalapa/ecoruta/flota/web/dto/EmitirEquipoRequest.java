package gt.muni.jalapa.ecoruta.flota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de un equipo para un vehiculo. */
public record EmitirEquipoRequest(
        @NotNull(message = "el vehiculo es obligatorio")
        @Schema(example = "1") Long vehiculoId,

        @NotBlank(message = "la etiqueta es obligatoria")
        @Size(max = 60, message = "la etiqueta no puede pasar de 60 caracteres")
        @Schema(example = "Tableta cabina BUS-01") String etiqueta) {
}
