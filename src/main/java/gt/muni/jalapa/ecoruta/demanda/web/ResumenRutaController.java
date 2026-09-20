package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.ResumenRutaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ResumenRutaResponse;
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
 * Datos de arranque de la pantalla del pasajero en una sola llamada (SCRUM-284).
 *
 * <p>Comparte el prefijo {@code /api/v1/rutas} con {@code RutaController}, pero
 * vive en el modulo {@code demanda} porque compone ruta, posicion y demanda. La
 * regla de seguridad ({@code /api/v1/rutas/**} publico) ya lo cubre.
 */
@Tag(name = "Demanda", description = "Resumen de arranque de la pantalla del pasajero")
@RestController
@RequestMapping("/api/v1/rutas")
@RequiredArgsConstructor
public class ResumenRutaController {

    private final ResumenRutaService resumenRutaService;

    @Operation(summary = "Resumen de arranque de una ruta",
            description = """
                    Publico: el pasajero es anonimo, no hay login.

                    En una sola respuesta: la ruta con sus paradas en orden, la
                    ultima posicion conocida del bus y las reservas activas
                    (ACTIVA o RENOVADA) por parada. La pantalla no necesita
                    encadenar mas consultas.

                    Si el bus aun no ha reportado posicion, `posicionActual` viene
                    null y la respuesta se entrega igual. Cada parada aparece en
                    `reservasActivas` aunque su conteo sea cero.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El resumen de la ruta"),
            @ApiResponse(responseCode = "400", description = "El rutaId no tiene formato de numero",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{rutaId}/resumen")
    public ResumenRutaResponse resumen(@PathVariable Long rutaId) {
        return resumenRutaService.resumir(rutaId);
    }
}
