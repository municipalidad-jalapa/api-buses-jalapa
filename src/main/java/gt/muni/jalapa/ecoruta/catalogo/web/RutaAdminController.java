package gt.muni.jalapa.ecoruta.catalogo.web;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CorreccionDeRutaService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirParadaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirTrazadoRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Correccion de rutas desde el panel municipal (QA 5.6). La regla
 * {@code /api/v1/admin/**} exige rol ADMIN.
 */
@Tag(name = "Panel municipal — rutas", description = "Corregir el trazado y las paradas de una ruta")
@RestController
@RequestMapping("/api/v1/admin/rutas")
@RequiredArgsConstructor
public class RutaAdminController {

    private final CorreccionDeRutaService correccion;

    @Operation(summary = "Todas las rutas con paradas y trazado, activas o no")
    @GetMapping
    public List<RutaResponse> listar() {
        return correccion.listarTodas();
    }

    @Operation(summary = "Reemplaza el trazado de la ruta",
            description = "Los puntos van en el orden del recorrido. El ETA usa el trazado nuevo en su siguiente calculo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La ruta con el trazado corregido"),
            @ApiResponse(responseCode = "400", description = "Menos de 2 puntos, o un punto fuera de Guatemala",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{rutaId}/trazado")
    public RutaResponse corregirTrazado(@PathVariable Long rutaId,
                                        @Valid @RequestBody CorregirTrazadoRequest peticion,
                                        Authentication autenticacion) {
        return correccion.corregirTrazado(rutaId, peticion, autenticacion.getName());
    }

    @Operation(summary = "Corrige el nombre y la ubicacion de una parada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La ruta con la parada corregida"),
            @ApiResponse(responseCode = "400", description = "Sin nombre, o ubicacion fuera de Guatemala",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "La parada no existe o no es de esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{rutaId}/paradas/{paradaId}")
    public RutaResponse corregirParada(@PathVariable Long rutaId,
                                       @PathVariable Long paradaId,
                                       @Valid @RequestBody CorregirParadaRequest peticion,
                                       Authentication autenticacion) {
        return correccion.corregirParada(rutaId, paradaId, peticion, autenticacion.getName());
    }
}
