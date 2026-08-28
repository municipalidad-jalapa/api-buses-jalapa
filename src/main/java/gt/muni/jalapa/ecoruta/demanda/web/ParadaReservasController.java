package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.ConsultaDeReservasService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservasDeParadaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la cola de espera por parada.
 *
 * <p>No valida existencia ni filtra estados: eso vive en
 * {@link ConsultaDeReservasService}. El rol {@code ROLE_CONDUCTOR} se
 * documenta aqui para el contrato OpenAPI; el cierre en {@code SecurityConfig}
 * va aparte, para no mezclar exposicion HTTP con autenticacion.
 */
@Tag(name = "Demanda — reservas",
        description = "Consulta de la cola de espera por parada")
@RestController
@RequestMapping("/api/v1/paradas")
@RequiredArgsConstructor
public class ParadaReservasController {

    private final ConsultaDeReservasService consultaDeReservasService;

    @Operation(summary = "Lista las reservas vigentes de una parada",
            description = """
                    Requiere ROLE_CONDUCTOR. La proteccion en SecurityConfig va
                    aparte: este contrato documenta el rol ahora para que el
                    cliente y OpenAPI no mientan cuando se cierre el endpoint.

                    Parada inexistente es 404, no lista vacia.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Demanda vigente de la parada"),
            @ApiResponse(responseCode = "404",
                    description = "La parada no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{paradaId}/reservas")
    public ReservasDeParadaResponse listar(@PathVariable Long paradaId) {
        return consultaDeReservasService.consultar(paradaId);
    }
}
