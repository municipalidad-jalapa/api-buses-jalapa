package gt.muni.jalapa.ecoruta.identidad.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.identidad.servicio.AutenticacionDeConductor;
import gt.muni.jalapa.ecoruta.identidad.web.dto.LoginConductorRequest;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionConductorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identidad — conductor", description = "Sesion de jornada del conductor (HU-Desarrollo-63)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthConductorController {

    private final AutenticacionDeConductor autenticacion;

    @Operation(summary = "Inicia la sesion del conductor",
            description = """
                    La app envia el idToken de Firebase. Si es valido y el usuario
                    tiene rol de conductor, el backend responde con su propio JWT
                    de jornada. Ese token, no el de Firebase, autoriza el panel.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion emitida"),
            @ApiResponse(responseCode = "401", description = """
                    idToken invalido, vencido, de otro proyecto, o usuario sin
                    rol de conductor.""",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/conductor")
    public SesionConductorResponse iniciarSesion(@Valid @RequestBody LoginConductorRequest peticion) {
        return autenticacion.iniciarSesion(peticion.idToken());
    }
}
