package gt.muni.jalapa.ecoruta.flota.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El GPS del bus: su uniqueId en Traccar, normalmente el IMEI. */
public record VincularGpsRequest(
        @NotBlank(message = "el GPS es obligatorio")
        @Size(max = 64, message = "el GPS no puede pasar de 64 caracteres")
        @Pattern(regexp = VincularGpsRequest.FORMATO, message = VincularGpsRequest.MENSAJE)
        @Schema(example = "860000000000001") String gps) {

    /** Lo que Traccar acepta como uniqueId sin sorpresas: letras, digitos, punto, guion. */
    public static final String FORMATO = "\s*[A-Za-z0-9._-]+\s*";
    public static final String MENSAJE = "el GPS solo lleva letras, numeros, punto o guion";
}
