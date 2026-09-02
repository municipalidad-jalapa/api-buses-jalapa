package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.DemandaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Demanda",
        description = "Registro y cancelación de pasajeros en espera"
)
@RestController
@RequestMapping("/api/v1/demanda")
@RequiredArgsConstructor
public class DemandaController {

    private final DemandaService demandaService;

    @Operation(
            summary = "Cancela una reserva de espera",
            description = """
                    Cancela un registro activo sin eliminarlo de la base de datos.
                    El pasajero se identifica temporalmente mediante
                    X-Dispositivo-Id mientras se integra la autenticación
                    definitiva del pasajero.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Reserva cancelada correctamente"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El registro pertenece a otro dispositivo",
                    content = @Content(
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "El registro no existe",
                    content = @Content(
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "El registro ya está cancelado o expirado",
                    content = @Content(
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    @DeleteMapping("/registros/{registroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @PathVariable Long registroId,
            @RequestHeader("X-Dispositivo-Id") String dispositivoId
    ) {
        demandaService.cancelarRegistro(registroId, dispositivoId);
    }
}
