package gt.muni.jalapa.ecoruta.seguridad.conductor;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.seguridad.conductor.dto.ConductorLoginRequest;
import gt.muni.jalapa.ecoruta.seguridad.conductor.dto.ConductorLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link Usuario}.
 *
 * <p>Login minimo para que un conductor obtenga un JWT. La ruta es
 * {@code /api/v1/auth/conductor/login} a proposito: {@code /api/v1/auth/login}
 * esta reservada para el login real con Firebase y no se pisa.
 *
 * <p>El 401 lo traduce {@code GlobalExceptionHandler} desde
 * {@link BadCredentialsException}; no se duplica ese manejo aqui.
 */
@Tag(name = "Seguridad — conductor",
        description = "Login provisional de conductor. TODO(SCRUM-134): borrar con el paquete.")
@RestController
@RequestMapping("/api/v1/auth/conductor")
@RequiredArgsConstructor
public class ConductorAuthController {

    private final ConductorLoginService conductorLoginService;

    @Operation(summary = "Emite un JWT de conductor",
            description = """
                    PROVISIONAL — TODO(SCRUM-134). No es el login de personas:
                    esa ruta es /api/v1/auth/login y sigue reservada para Firebase.

                    Usuario inexistente, inactivo, con rol distinto de CONDUCTOR
                    o contrasena incorrecta responden 401 con el mismo cuerpo.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWT emitido"),
            @ApiResponse(responseCode = "401",
                    description = "Credenciales invalidas",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ConductorLoginResponse login(@Valid @RequestBody ConductorLoginRequest peticion) {
        String token = conductorLoginService.login(peticion.username(), peticion.password());
        return new ConductorLoginResponse(token);
    }
}
