package gt.muni.jalapa.ecoruta.demanda.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DeclararNoAbordoRequest(

        @NotBlank
        String dispositivoId
) {
}