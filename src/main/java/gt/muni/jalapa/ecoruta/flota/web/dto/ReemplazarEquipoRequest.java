package gt.muni.jalapa.ecoruta.flota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio del equipo fisico de un vehiculo: revoca el actual y emite otro. */
public record ReemplazarEquipoRequest(
        @NotBlank(message = "la etiqueta es obligatoria")
        @Size(max = 60, message = "la etiqueta no puede pasar de 60 caracteres")
        @Schema(example = "Tableta nueva BUS-01") String etiqueta) {
}
