package gt.muni.jalapa.ecoruta.panel.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.panel.servicio.PanelService;
import gt.muni.jalapa.ecoruta.panel.web.dto.PanelRutasResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervision de la operacion en curso para el panel municipal (HU-79).
 *
 * <p>Vive en su propio prefijo {@code /api/v1/panel} porque no es publico:
 * exige ROLE_ADMIN. El {@code AdminBootstrapFilter} lo cubre mientras no
 * aterrice SCRUM-134; el controller no cambia cuando eso ocurra.
 */
@Tag(name = "Panel municipal", description = "Supervision de la operacion en curso")
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
public class PanelController {

    private final PanelService panelService;

    @Operation(summary = "Lista las rutas activas con su operacion",
            description = """
                    Requiere ROLE_ADMIN. Provisional hasta SCRUM-134 (Firebase).

                    En una sola respuesta: cada ruta activa, el bus asignado, su
                    ultima posicion y si transmite, mas las reservas vigentes
                    por parada. El tablero no encadena mas consultas.

                    Si la ruta no tiene bus activo, `vehiculoId` y `posicion`
                    vienen null y `transmitiendo` es false. Una posicion mas
                    vieja que ecoruta.panel.umbral-sin-transmitir-minutos
                    tambien marca `transmitiendo` en false. Cada parada aparece
                    en `reservasPorParada` aunque su conteo sea cero.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rutas activas con su operacion"),
            @ApiResponse(responseCode = "401", description = "Sin credencial de administrador",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "La credencial no tiene rol administrador",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/rutas")
    public PanelRutasResponse listar() {
        return panelService.listar();
    }
}
