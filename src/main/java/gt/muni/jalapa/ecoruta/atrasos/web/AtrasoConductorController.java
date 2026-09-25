package gt.muni.jalapa.ecoruta.atrasos.web;

import gt.muni.jalapa.ecoruta.atrasos.servicio.AvisosDeAtraso;
import gt.muni.jalapa.ecoruta.atrasos.web.dto.AtrasoDtos.AtrasoResponse;
import gt.muni.jalapa.ecoruta.atrasos.web.dto.AtrasoDtos.ReportarAtrasoRequest;
import gt.muni.jalapa.ecoruta.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El piloto avisa que viene demorado (SCRUM-26, bloque E, criterio 2).
 *
 * <p>Cuelga de {@code /api/v1/conductor/**}, que ya exige rol de piloto. La
 * ruta no viaja en la peticion: es la que tiene asignada su cuenta, asi que no
 * puede reportar un atraso en la ruta de otro.
 */
@Tag(name = "Conductor — atrasos", description = "Aviso de demora del piloto (SCRUM-26)")
@RestController
@RequestMapping("/api/v1/conductor/atrasos")
@RequiredArgsConstructor
public class AtrasoConductorController {

    private final AvisosDeAtraso avisos;

    @Operation(summary = "El piloto reporta un atraso",
            description = """
                    Motivo (trafico o incidente) y demora estimada en minutos. Un aviso
                    nuevo reemplaza al anterior de esa ruta. El pasajero lo ve junto al
                    tiempo estimado mientras dura.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Atraso reportado"),
            @ApiResponse(responseCode = "422", description = "Motivo o demora invalidos, o el piloto no tiene ruta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<AtrasoResponse> reportar(@RequestBody ReportarAtrasoRequest peticion,
                                                   Authentication autenticado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(avisos.reportar(autenticado.getName(), peticion));
    }

    @Operation(summary = "El aviso vigente de la ruta del piloto",
            description = "204 cuando no hay ningun atraso reportado.")
    @GetMapping("/vigente")
    public ResponseEntity<AtrasoResponse> vigente(Authentication autenticado) {
        return avisos.vigenteDelPiloto(autenticado.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "El piloto retira el aviso",
            description = "Se usa cuando el servicio ya se normalizo antes de lo estimado.")
    @DeleteMapping("/vigente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(Authentication autenticado) {
        avisos.cancelar(autenticado.getName());
    }
}
