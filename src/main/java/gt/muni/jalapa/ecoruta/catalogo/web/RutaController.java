package gt.muni.jalapa.ecoruta.catalogo.web;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CatalogoService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.ApiError;
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

import java.util.List;

@Tag(name = "Catalogo", description = "Rutas y paradas del servicio")
@RestController
@RequestMapping("/api/v1/rutas")
@RequiredArgsConstructor
public class RutaController {

    private final CatalogoService catalogoService;

    @Operation(summary = "Lista las rutas activas con sus paradas",
            description = """
                    Publico: el pasajero es anonimo, no hay login.

                    Las paradas vienen en el orden del recorrido. El mapa (HU-50)
                    dibuja la linea de la ruta uniendolas en esa secuencia.

                    Las coordenadas se exponen como latitud y longitud con nombre, no
                    como un par posicional: invertirlas es el error clasico del dominio
                    (ADR-007) y con nombres no puede pasar en silencio.""")
    @ApiResponse(responseCode = "200", description = "Rutas activas")
    @GetMapping
    public List<RutaResponse> listar() {
        return catalogoService.listarActivas();
    }

    @Operation(summary = "Consulta una ruta por su identificador",
            description = "Publico. Devuelve la ruta este activa o no.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La ruta"),
            @ApiResponse(responseCode = "404", description = "No existe esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{rutaId}")
    public RutaResponse buscar(@PathVariable Long rutaId) {
        return catalogoService.buscar(rutaId);
    }
}
