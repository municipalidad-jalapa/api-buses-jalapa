package gt.muni.jalapa.ecoruta.eta.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.eta.servicio.EtaService;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
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
 * Tiempo estimado de llegada por ruta (SCRUM-166, HU Desarrollo-71). Lo consumen
 * la pantalla del pasajero y la del conductor. La regla {@code /api/v1/rutas/**}
 * publico ya lo cubre.
 */
@Tag(name = "ETA", description = "Tiempo estimado de llegada del bus a sus paradas")
@RestController
@RequestMapping("/api/v1/rutas")
@RequiredArgsConstructor
public class EtaController {

    private final EtaService etaService;

    @Operation(summary = "ETA del bus de una ruta a sus paradas pendientes",
            description = """
                    Publico. Minutos estimados desde la ultima posicion del bus de la
                    ruta hasta cada parada pendiente, medidos siguiendo el trazado y
                    con la velocidad observada del bus en el tramo reciente.

                    `confiable` es false cuando no hubo historial de velocidad y se
                    uso la velocidad promedio del recorrido. Si la ultima posicion es
                    demasiado vieja, o el bus aun no reporta, `minutos` viene null:
                    no se inventa un numero.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El ETA de la ruta"),
            @ApiResponse(responseCode = "400", description = "El rutaId no tiene formato de numero",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{rutaId}/eta")
    public EtaRutaResponse eta(@PathVariable Long rutaId) {
        return etaService.consultar(rutaId);
    }
}
