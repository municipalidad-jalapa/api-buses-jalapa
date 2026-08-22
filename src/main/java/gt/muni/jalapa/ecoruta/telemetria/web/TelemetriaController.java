package gt.muni.jalapa.ecoruta.telemetria.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoAutenticado;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.LoteAceptadoResponse;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.LotePosicionesRequest;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Telemetria", description = "Ingesta y consulta de la posicion del bus")
@RestController
@RequestMapping("/api/v1/telemetria")
@RequiredArgsConstructor
public class TelemetriaController {

    private final TelemetriaService telemetriaService;
    private final DifusorDePosiciones difusor;

    @Operation(summary = "Recibe un lote de posiciones del equipo a bordo",
            description = """
                    Autenticado con la credencial propia del equipo (SCRUM-142): ya no
                    interviene ningun rol de persona.

                    Offline-first: el equipo acumula lecturas sin cobertura y las manda
                    juntas, posiblemente desordenadas. Se responde 202 aunque alguna se
                    descarte por tener el reloj fuera de la ventana de mas/menos 12 h.

                    El vehiculo al que se atribuyen sale de la credencial, no del cuerpo.""")
    @SecurityRequirement(name = "credencialEquipo")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Lote aceptado"),
            @ApiResponse(responseCode = "400", description = "Cuerpo invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401",
                    description = "Credencial ausente, invalida o revocada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "El equipo no tiene vehiculo asignado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/posiciones")
    public ResponseEntity<LoteAceptadoResponse> ingestar(
            @AuthenticationPrincipal EquipoAutenticado equipo,
            @Valid @RequestBody LotePosicionesRequest peticion) {

        LoteAceptadoResponse resumen = telemetriaService.ingestar(equipo, peticion.posiciones());
        return ResponseEntity.accepted().body(resumen);
    }

    @Operation(summary = "Ultima posicion conocida del bus",
            description = """
                    Publico. Responde 204 mientras no haya llegado ninguna posicion.
                    Sin vehiculoId devuelve la mas reciente de la flota.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posicion vigente"),
            @ApiResponse(responseCode = "204", description = "Todavia no hay ninguna posicion")
    })
    @GetMapping("/posicion")
    public ResponseEntity<PosicionActualResponse> posicionVigente(
            @RequestParam(required = false) Long vehiculoId) {

        return telemetriaService.posicionVigente(vehiculoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @Operation(summary = "Stream de posiciones en tiempo real",
            description = """
                    Publico. Emite Server-Sent Events: el navegador se conecta con
                    EventSource y recibe cada posicion apenas se ingesta, sin preguntar
                    cada pocos segundos (ADR-008).

                    Al conectarse se envia de inmediato la posicion vigente, para que el
                    mapa no arranque vacio. Despues llega un evento 'posicion' por cada
                    lote aceptado, y un comentario de latido cuando el bus esta parado.

                    Como respaldo, si el stream no conecta, esta GET /telemetria/posicion.""")
    @ApiResponse(responseCode = "200", description = "Stream abierto (text/event-stream)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse respuesta) {
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // ADR-008 lo deja escrito: "si el proxy acumula la respuesta, el stream
        // nunca llega". Esta cabecera es la mitad que le toca a la aplicacion;
        // el proxy_buffering off del Ingress es de SCRUM-149 (DevOps).
        respuesta.setHeader("X-Accel-Buffering", "no");

        SseEmitter emisor = difusor.suscribir();
        // Sin esto el mapa se queda en blanco hasta el siguiente lote.
        telemetriaService.posicionVigente(null)
                .ifPresent(vigente -> difusor.enviarA(emisor, vigente));
        return emisor;
    }
}
