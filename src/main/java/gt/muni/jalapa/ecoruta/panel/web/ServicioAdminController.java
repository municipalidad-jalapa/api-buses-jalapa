package gt.muni.jalapa.ecoruta.panel.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.panel.servicio.ServicioMunicipal;
import gt.muni.jalapa.ecoruta.panel.web.dto.ServicioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portada del panel municipal (SCRUM-173): el servicio completo, no una ruta. */
@Tag(name = "Panel municipal", description = "Supervision del servicio completo")
@RestController
@RequiredArgsConstructor
public class ServicioAdminController {

    private final ServicioMunicipal servicio;

    @Operation(summary = "Estado de todas las rutas activas con su bus",
            description = "Solo administrador. El rol no esta atado a ninguna ruta: ve todas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio completo"),
            @ApiResponse(responseCode = "401", description = "Sin sesion",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sesion sin rol de administrador",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/v1/admin/servicio")
    public ServicioResponse servicio() {
        return servicio.estado();
    }
}
