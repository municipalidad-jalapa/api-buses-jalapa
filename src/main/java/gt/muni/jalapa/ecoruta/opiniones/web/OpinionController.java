package gt.muni.jalapa.ecoruta.opiniones.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.opiniones.dominio.TipoOpinion;
import gt.muni.jalapa.ecoruta.opiniones.servicio.LimiteDeOpinionesExcedido;
import gt.muni.jalapa.ecoruta.opiniones.servicio.OpinionService;
import gt.muni.jalapa.ecoruta.opiniones.servicio.OpinionService.Filtros;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.AtendidaResponse;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.CrearOpinionRequest;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.OpinionCreadaResponse;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.PaginaDeOpiniones;
import gt.muni.jalapa.ecoruta.pasajeros.seguridad.PasajeroJwtAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Opiniones del servicio (SCRUM-26, bloque A). Registrar es publico y anonimo;
 * listar y atender es del panel municipal (rol administrador). La regla de
 * acceso vive en {@code OpinionesSecurityConfig}.
 */
@Tag(name = "Opiniones", description = "Quejas, comentarios y calificaciones del pasajero (SCRUM-26)")
@RestController
@RequestMapping("/api/v1/opiniones")
@RequiredArgsConstructor
public class OpinionController {

    private final OpinionService servicio;

    @Operation(summary = "Registra una opinion sobre el servicio",
            description = """
                    Publico y sin cuenta: se atribuye al identificador anonimo del navegador
                    (`X-Dispositivo-Id`). El vehiculo lo resuelve el servidor a partir de la
                    ruta y se devuelve en la respuesta. Al menos texto o estrellas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Opinion registrada"),
            @ApiResponse(responseCode = "422", description = "Sin texto ni calificacion, ruta inexistente o datos invalidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Limite de envios excedido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<OpinionCreadaResponse> registrar(
            @RequestHeader(value = "X-Dispositivo-Id", required = false) String dispositivoId,
            @RequestBody CrearOpinionRequest peticion,
            Authentication autenticado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.registrar(dispositivoId, pasajeroDe(autenticado), peticion));
    }

    /** Con sesion de pasajero la opinion queda tambien en su cuenta (bloque B). */
    private static Long pasajeroDe(Authentication autenticado) {
        if (autenticado == null || autenticado.getAuthorities().stream()
                .noneMatch(a -> PasajeroJwtAuthFilter.ROL.equals(a.getAuthority()))) {
            return null;
        }
        return Long.valueOf(autenticado.getName());
    }

    @Operation(summary = "Lista las opiniones para el panel municipal",
            description = "Solo administrador. De la mas reciente a la mas antigua; el texto sale neutralizado.")
    @GetMapping
    public PaginaDeOpiniones listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) Long vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new Filtros(TipoOpinion.de(tipo), rutaId, vehiculoId, desde, hasta, pagina, tamano));
    }

    @Operation(summary = "Marca una opinion como atendida",
            description = "Solo administrador. Registra quien y cuando; si ya estaba atendida, conserva el primer registro.")
    @PatchMapping("/{id}/atendida")
    public AtendidaResponse atender(@PathVariable Long id, Authentication autenticado) {
        return servicio.marcarAtendida(id, autenticado.getName());
    }

    @ExceptionHandler(LimiteDeOpinionesExcedido.class)
    public ResponseEntity<ApiError> demasiadas(LimiteDeOpinionesExcedido ex, HttpServletRequest peticion) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(), peticion.getRequestURI()));
    }
}
