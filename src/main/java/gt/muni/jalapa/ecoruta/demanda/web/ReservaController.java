package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.ReservaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reserva de un lugar en la parada (SCRUM-306 / HU Desarrollo-134).
 *
 * <p>Contrato vigente de Jira: {@code POST /api/v1/reservas}. El
 * {@code POST /api/v1/demanda/registros} que traia HU-57 se elimino al integrar el
 * sprint 5: era la misma reserva con otro nombre y sin geocerca.
 */
@Tag(name = "Demanda", description = "Reservas de espera en parada")
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    @Operation(summary = "Indica que el pasajero está esperando en una parada",
            description = """
                    Publico: el pasajero es anonimo.

                    Crea una reserva en estado ACTIVA asociada al dispositivo y a
                    la parada. El dispositivo debe estar dentro de la geocerca
                    configurada (ecoruta.demanda.geocerca-metros) y no puede tener
                    otra reserva vigente (ACTIVA o RENOVADA).""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada"),
            @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o invalidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "La parada no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422",
                    description = "Dispositivo lejos de la parada o con una reserva vigente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(peticion));
    }

    @Operation(summary = "Renueva una reserva vigente",
            description = """
                    Publico, pero solo el dispositivo que la creo puede renovarla
                    (HU-135): se identifica con la cabecera X-Dispositivo-Id.

                    Extiende la expiracion otro periodo completo
                    (ecoruta.demanda.ttl-minutos) y responde 200 con el nuevo
                    expiraEn. La reserva conserva su identificador y pasa a
                    RENOVADA. Si ya vencio o no esta vigente responde 422.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva renovada"),
            @ApiResponse(responseCode = "403", description = "La reserva es de otro dispositivo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe una reserva con ese id",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "La reserva ya vencio o no esta vigente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/renovacion")
    public ReservaResponse renovar(@PathVariable Long id,
                                   @RequestHeader("X-Dispositivo-Id") String dispositivoId) {
        return reservaService.renovar(id, dispositivoId);
    }

    @Operation(summary = "Cancela una reserva vigente",
            description = """
                    Publico, pero solo el dispositivo que la creo puede cancelarla
                    (HU-124 / SCRUM-172): se identifica con la cabecera X-Dispositivo-Id.

                    La reserva no se borra: pasa a CANCELADA y guarda cuando se
                    cancelo, para que quede la traza de la demanda que se solto.
                    Una reserva en ABORDO no se puede cancelar.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "403", description = "La reserva es de otro dispositivo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe una reserva con ese id",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422",
                    description = "Ya estaba cancelada, abordada, expirada o no esta vigente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id,
                         @RequestHeader("X-Dispositivo-Id") String dispositivoId) {
        reservaService.cancelar(id, dispositivoId);
    }
}
