package gt.muni.jalapa.ecoruta.identidad.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.identidad.servicio.AutenticacionDeAdministrador;
import gt.muni.jalapa.ecoruta.identidad.web.dto.LoginAdminRequest;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identidad — panel municipal", description = "Sesion del administrador municipal (SCRUM-173)")
@RestController
@RequiredArgsConstructor
public class AuthAdminController {

    private final AutenticacionDeAdministrador autenticacion;

    @Operation(summary = "Inicia la sesion del panel municipal",
            description = """
                    El panel envia el idToken de Firebase. Si la cuenta tiene rol de
                    administrador, el backend responde con su propio JWT. La sesion
                    vence tras `inactividadMinutos` sin renovarse.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion emitida"),
            @ApiResponse(responseCode = "401", description = "idToken invalido o vencido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "La cuenta existe pero no es de administrador",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/v1/auth/admin")
    public SesionAdminResponse iniciarSesion(@Valid @RequestBody LoginAdminRequest peticion) {
        return autenticacion.iniciarSesion(peticion.idToken());
    }

    @Operation(summary = "Renueva la sesion mientras hay actividad en el panel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion renovada"),
            @ApiResponse(responseCode = "401", description = "Sin sesion o ya vencida por inactividad",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/v1/admin/sesion/renovacion")
    public SesionAdminResponse renovar(Authentication autenticado) {
        return autenticacion.renovar(autenticado.getName());
    }
}
