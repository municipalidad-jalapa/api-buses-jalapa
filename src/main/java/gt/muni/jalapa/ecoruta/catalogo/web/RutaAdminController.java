package gt.muni.jalapa.ecoruta.catalogo.web;

import gt.muni.jalapa.ecoruta.calles.RedDeCalles;
import gt.muni.jalapa.ecoruta.catalogo.servicio.AltaDeRutaService;
import gt.muni.jalapa.ecoruta.catalogo.servicio.CorreccionDeRutaService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.AjustarTrazoRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirParadaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirTrazadoRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CrearRutaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.PublicarRutaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.TrazoAjustadoResponse;
import gt.muni.jalapa.ecoruta.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final AltaDeRutaService alta;
    private final RedDeCalles calles;

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

    @Operation(summary = "Crea una ruta nueva, como borrador",
            description = "Nace inactiva: el pasajero no la ve hasta publicarla con paradas y trazado.")
    @PostMapping
    public ResponseEntity<RutaResponse> crear(@Valid @RequestBody CrearRutaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alta.crear(peticion.nombre()));
    }

    @Operation(summary = "Agrega una parada al final del recorrido")
    @PostMapping("/{rutaId}/paradas")
    public ResponseEntity<RutaResponse> agregarParada(@PathVariable Long rutaId,
                                                      @Valid @RequestBody CorregirParadaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alta.agregarParada(rutaId, peticion));
    }

    @Operation(summary = "Elimina una parada del recorrido",
            description = """
                    La parada deja de verse y de contar para el ETA y el conductor, y
                    las siguientes suben un lugar. Su historial (reservas, atenciones)
                    se conserva. Las reservas vigentes en ella se cancelan.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La ruta sin la parada"),
            @ApiResponse(responseCode = "404", description = "La parada no existe o no es de esa ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "La ruta esta publicada y se quedaria con menos de 2 paradas",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{rutaId}/paradas/{paradaId}")
    public RutaResponse eliminarParada(@PathVariable Long rutaId,
                                       @PathVariable Long paradaId,
                                       Authentication autenticacion) {
        return alta.eliminarParada(rutaId, paradaId, autenticacion.getName());
    }

    @Operation(summary = "Ajusta a las calles un trazo dibujado a mano",
            description = """
                    El lapiz del editor: el trazo se engancha a los cruces de la red de
                    calles de Jalapa (OpenStreetMap, en la base) y se une por el camino
                    mas corto. No consulta ningun servicio externo. Si no hay calle cerca
                    devuelve el mismo trazo con ajustado = false.""")
    @PostMapping("/ajuste-a-calles")
    public TrazoAjustadoResponse ajustarACalles(@Valid @RequestBody AjustarTrazoRequest peticion) {
        List<PuntoResponse> trazo = peticion.puntos().stream()
                .map(p -> new PuntoResponse(p.latitud(), p.longitud()))
                .toList();
        return calles.ajustar(trazo)
                .map(ajustado -> new TrazoAjustadoResponse(ajustado, true))
                .orElseGet(() -> new TrazoAjustadoResponse(trazo, false));
    }

    @Operation(summary = "Elimina una ruta",
            description = """
                    Deja de verse en el panel, en el mapa del pasajero y para el conductor.
                    El bus y los conductores que la tenian quedan sin ruta y las reservas
                    vigentes se cancelan. El historial se conserva en los reportes.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ruta eliminada"),
            @ApiResponse(responseCode = "404", description = "No existe esa ruta o ya se elimino",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{rutaId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long rutaId, Authentication autenticacion) {
        alta.eliminar(rutaId, autenticacion.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publica u oculta la ruta para el pasajero",
            description = "Publicar pide al menos 2 paradas y el trazado.")
    @PutMapping("/{rutaId}/publicacion")
    public RutaResponse publicar(@PathVariable Long rutaId, @Valid @RequestBody PublicarRutaRequest peticion) {
        return alta.publicar(rutaId, peticion.activa());
    }
}
