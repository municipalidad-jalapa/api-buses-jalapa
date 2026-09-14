package gt.muni.jalapa.ecoruta.tiempo.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.tiempo.servicio.TiempoEstimadoService;
import gt.muni.jalapa.ecoruta.tiempo.web.dto.TiempoEstimadoResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tiempo estimado", description = "Minutos restantes segun horarios y detenciones (HU-72)")
@RestController
@RequestMapping("/api/v1/rutas")
@RequiredArgsConstructor
public class TiempoEstimadoController {

    private final TiempoEstimadoService tiempoEstimado;

    @Operation(summary = "Tiempo estimado en minutos",
            description = """
                    Publico: el pasajero es anonimo.

                    Si el bus sigue en el origen, el minuto se cuenta desde la
                    proxima salida programada del tipo de dia, no desde ahora.
                    Al trayecto se le suman las detenciones de las paradas
                    intermedias. Los parametros se leen de la base.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Minutos estimados"),
            @ApiResponse(responseCode = "404", description = "No hay parametros para esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{rutaId}/tiempo-estimado")
    public TiempoEstimadoResponse estimar(@PathVariable Long rutaId,
                                          @RequestParam double latitud,
                                          @RequestParam double longitud) {
        return new TiempoEstimadoResponse(rutaId, tiempoEstimado.minutos(rutaId, latitud, longitud));
    }
}
