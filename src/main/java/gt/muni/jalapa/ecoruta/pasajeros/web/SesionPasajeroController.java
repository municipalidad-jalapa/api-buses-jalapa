package gt.muni.jalapa.ecoruta.pasajeros.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero.Sesion;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero.Vinculacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sesión del pasajero", description = "Cuenta opcional con Google (SCRUM-26, bloque B)")
@RestController
@RequestMapping("/api/v1/sesion/pasajero")
@RequiredArgsConstructor
public class SesionPasajeroController {

    private final SesionDePasajero sesion;

    public record IniciarRequest(@NotBlank(message = "el idToken es obligatorio") String idToken) {
    }

    public record VincularRequest(@NotBlank(message = "el dispositivoId es obligatorio") String dispositivoId) {
    }

    @Operation(summary = "Inicia la sesion opcional del pasajero",
            description = """
                    La web inicia sesion con Google en Firebase y envia el idToken. El backend
                    responde con su propio JWT con rol pasajero. La contrasena nunca pasa por
                    EcoRuta. Sin cuenta, todo sigue funcionando como invitado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion emitida"),
            @ApiResponse(responseCode = "401", description = "idToken invalido o vencido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public Sesion iniciar(@Valid @RequestBody IniciarRequest peticion) {
        return sesion.iniciar(peticion.idToken());
    }

    @Operation(summary = "Asocia a la cuenta las reservas y opiniones de este navegador",
            description = "Idempotente: repetirla no duplica ni reasigna lo que ya es de otra cuenta.")
    @PostMapping("/vincular")
    public Vinculacion vincular(@Valid @RequestBody VincularRequest peticion, Authentication autenticado) {
        return sesion.vincular(Long.valueOf(autenticado.getName()), peticion.dispositivoId());
    }
}
