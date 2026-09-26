package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.PanelConductorService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.PanelConductorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel del conductor (HU-62, HU-75, HU-76). La regla
 * {@code /api/v1/conductor/**} exige rol CONDUCTOR.
 */
@Tag(name = "Conductor — panel", description = "Paradas del recorrido con reservas, ETA y atencion")
@RestController
@RequiredArgsConstructor
public class PanelConductorController {

    private final PanelConductorService panel;

    @Operation(summary = "Panel del conductor",
            description = """
                    Las paradas de la ruta asignada al conductor autenticado, en el
                    orden del recorrido, con las reservas activas de cada una, los
                    minutos estimados de llegada del bus (null si no hay estimacion)
                    y si ya se marco atendida hoy.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El panel"),
            @ApiResponse(responseCode = "401", description = "Sin sesion de conductor",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol de conductor o sin ruta asignada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/v1/conductor/panel")
    public PanelConductorResponse panel(Authentication autenticacion) {
        return panel.armar(autenticacion.getName());
    }
}
