package gt.muni.jalapa.ecoruta.catalogo.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Una ruta nueva desde el panel municipal: solo el nombre; el resto se dibuja despues. */
public record CrearRutaRequest(
        @Schema(example = "RUTA NORTE")
        @NotBlank(message = "La ruta necesita un nombre")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre) {
}
