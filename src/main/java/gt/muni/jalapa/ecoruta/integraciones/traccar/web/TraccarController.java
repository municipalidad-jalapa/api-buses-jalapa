package gt.muni.jalapa.ecoruta.integraciones.traccar.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.integraciones.traccar.servicio.RecepcionTraccar;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.LoteAceptadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Integraciones — Traccar", description = "Reenvio de posiciones del GPS real (SCRUM-24)")
@RestController
@RequestMapping("/api/v1/integraciones/traccar")
@RequiredArgsConstructor
public class TraccarController {

    private final RecepcionTraccar recepcion;
    private final ObjectMapper json;

    @Operation(summary = "Recibe el reenvio de posiciones de Traccar",
            description = """
                    Lo llama el servidor Traccar (`forward.url` + `forward.type=json`) con la
                    cabecera `X-Traccar-Token`. El cuerpo real es `{position, device}` (no un
                    objeto plano). Acepta un reenvio o un arreglo de ellos.

                    La posicion se atribuye al vehiculo del equipo asociado al
                    `device.uniqueId`. Un 202 no implica que todas se guardaron: la
                    respuesta resume recibidas, aceptadas y descartadas (fuera de la
                    ventana de 12 h o reenvio repetido).""",
            parameters = @Parameter(name = "X-Traccar-Token", in = ParameterIn.HEADER, required = true,
                    description = "Secreto de integracion. No es la credencial del equipo a bordo."))
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reenvio procesado"),
            @ApiResponse(responseCode = "400", description = "Cuerpo mal formado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Credencial de integracion ausente o invalida",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Dispositivo no asociado a un equipo, o datos invalidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/posiciones")
    public ResponseEntity<LoteAceptadoResponse> recibir(@RequestBody JsonNode cuerpo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recepcion.recibir(leer(cuerpo)));
    }

    /** Traccar manda un objeto por posicion; se acepta tambien un arreglo. */
    private List<ReenvioTraccar> leer(JsonNode cuerpo) {
        List<JsonNode> nodos = new ArrayList<>();
        if (cuerpo.isArray()) {
            cuerpo.forEach(nodos::add);
        } else if (cuerpo.isObject()) {
            nodos.add(cuerpo);
        }
        if (nodos.isEmpty()) {
            throw new HttpMessageNotReadableException("Se esperaba un reenvio de Traccar o un arreglo de ellos.", (HttpInputMessage) null);
        }
        try {
            List<ReenvioTraccar> reenvios = new ArrayList<>();
            for (JsonNode nodo : nodos) {
                reenvios.add(json.treeToValue(nodo, ReenvioTraccar.class));
            }
            return reenvios;
        } catch (JsonProcessingException ex) {
            throw new HttpMessageNotReadableException("Reenvio de Traccar mal formado.", ex, (HttpInputMessage) null);
        }
    }
}
