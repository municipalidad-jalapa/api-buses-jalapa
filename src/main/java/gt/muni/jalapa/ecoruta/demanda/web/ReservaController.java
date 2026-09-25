package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.ReservaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.DeclararNoAbordoRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.DetalleReservaResponse;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
import gt.muni.jalapa.ecoruta.pasajeros.seguridad.PasajeroJwtAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reserva de un lugar en la parada.
 *
 * <p>SCRUM-306 / HU-134 crea la reserva.</p>
 * <p>HU-135 permite renovarla.</p>
 * <p>HU-124 permite cancelarla.</p>
 * <p>HU-76 permite consultar su estado y registrar
 * la declaracion de que el pasajero no abordo.</p>
 */
@Tag(
        name = "Demanda — reservas",
        description = "Reserva de lugar en la parada con vigencia y renovacion"
)
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * SCRUM-306 / HU-134 / Desarrollo-135.
     * Crear una reserva.
     */
    @Operation(
            summary = "Indica que el pasajero esta esperando en una parada",
            description = """
                    Publico: el pasajero es anonimo, se identifica con el id de su
                    dispositivo. La reserva nace ACTIVA y expira a los pocos minutos;
                    ese instante viaja en expiraEn. Una reserva EXPIRADA del mismo
                    dispositivo no impide crear otra.

                    El dispositivo debe estar dentro de la geocerca configurada
                    y no puede tener otra reserva vigente.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Reserva creada"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Campos obligatorios ausentes o invalidos",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "La parada no existe",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Dispositivo lejos de la parada o con una reserva vigente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @Valid
            @RequestBody
            CrearReservaRequest peticion
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        reservaService.crear(peticion)
                );
    }

    /**
     * HU-135.
     * Renovar una reserva vigente.
     */
    @Operation(
            summary = "Renueva una reserva vigente",
            description = """
                    Solo el dispositivo que creo la reserva puede renovarla.

                    Se identifica mediante la cabecera X-Dispositivo-Id.

                    La reserva conserva su identificador,
                    extiende su vigencia (ecoruta.demanda.renovacion-minutos,
                    15 por defecto) y pasa a RENOVADA.
                    Si ya expiro o no esta activa responde 422.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reserva renovada"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "La reserva pertenece a otro dispositivo",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No existe una reserva con ese id",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "La reserva ya vencio o no esta vigente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @PostMapping("/{id}/renovacion")
    public ReservaResponse renovar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dispositivo-Id", required = false)
            String dispositivoId,
            Authentication autenticado
    ) {
        return reservaService.renovar(
                id,
                dispositivoId,
                PasajeroJwtAuthFilter.pasajeroDe(autenticado)
        );
    }

    /**
     * HU-124.
     * Cancelar una reserva.
     */
    @Operation(
            summary = "Cancela una reserva vigente",
            description = """
                    Solo el dispositivo que creo la reserva puede cancelarla.

                    La reserva no se elimina.
                    Pasa al estado CANCELADA y conserva la fecha
                    en que ocurrio la cancelacion.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Reserva cancelada"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "La reserva pertenece a otro dispositivo",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No existe una reserva con ese id",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "La reserva ya estaba cancelada o no esta vigente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dispositivo-Id", required = false)
            String dispositivoId,
            Authentication autenticado
    ) {

        reservaService.cancelar(
                id,
                dispositivoId,
                PasajeroJwtAuthFilter.pasajeroDe(autenticado)
        );
    }

    /**
     * HU-76.
     *
     * Permite que el pasajero consulte
     * el estado actual de su reserva.
     *
     * Esto permite detectar cuando el conductor
     * ya marco la reserva como ABORDO.
     */
    @Operation(
            summary = "Consulta el estado de una reserva"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reserva encontrada"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "La reserva no existe o no pertenece al dispositivo",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @GetMapping("/{reservaId}")
    public ResponseEntity<DetalleReservaResponse> consultar(
            @PathVariable Long reservaId,
            @RequestParam(required = false) String dispositivoId,
            Authentication autenticado
    ) {

        return ResponseEntity.ok(
                reservaService.consultar(
                        reservaId,
                        dispositivoId,
                        PasajeroJwtAuthFilter.pasajeroDe(autenticado)
                )
        );
    }

    /**
     * SCRUM-26, bloque B.2, criterio 4.
     *
     * Reservas de la cuenta autenticada. Sin sesion de pasajero no hay
     * nada que listar: el invitado consulta las suyas por identificador
     * de reserva y de dispositivo.
     */
    @Operation(
            summary = "Lista las reservas de la cuenta del pasajero",
            description = """
                    Requiere sesion de pasajero (bloque B). Devuelve solo las
                    reservas vinculadas a esa cuenta, de la mas reciente a la
                    mas antigua.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reservas de la cuenta"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Sin sesion de pasajero",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @GetMapping("/mias")
    public List<DetalleReservaResponse> mias(
            Authentication autenticado
    ) {

        Long pasajeroId =
                PasajeroJwtAuthFilter.pasajeroDe(autenticado);

        if (pasajeroId == null) {
            throw new AccessDeniedException(
                    "Inicia sesión para ver tus reservas."
            );
        }

        return reservaService.misReservas(pasajeroId);
    }

    /**
     * HU-76.
     *
     * El pasajero declara que considera
     * que no logro abordar.
     *
     * Si posteriormente el conductor confirma
     * que si abordo, la declaracion se conserva
     * para auditoria.
     */
    @Operation(
            summary = "El pasajero declara que no abordo"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Declaracion registrada"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "La reserva no existe o no pertenece al dispositivo",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiError.class
                            )
                    )
            )
    })
    @PostMapping("/{reservaId}/declaracion-no-abordo")
    public ResponseEntity<DetalleReservaResponse> declararNoAbordo(
            @PathVariable Long reservaId,
            @Valid
            @RequestBody
            DeclararNoAbordoRequest peticion,
            Authentication autenticado
    ) {

        return ResponseEntity.ok(
                reservaService.declararNoAbordo(
                        reservaId,
                        peticion.dispositivoId(),
                        PasajeroJwtAuthFilter.pasajeroDe(autenticado)
                )
        );
    }
}
