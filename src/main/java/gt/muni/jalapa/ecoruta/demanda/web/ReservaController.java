package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.ReservaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.DeclararNoAbordoRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.DetalleReservaResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints relacionados con reservas.
 */
@Tag(
        name = "Demanda",
        description = "Reservas de espera en parada"
)
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * Crear reserva.
     */
    @Operation(
            summary = "Indica que el pasajero esta esperando en una parada"
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
     * HU-76.
     *
     * El pasajero consulta su reserva.
     */
    @GetMapping("/{reservaId}")
    public ResponseEntity<DetalleReservaResponse> consultar(
            @PathVariable Long reservaId,
            @RequestParam String dispositivoId
    ) {

        return ResponseEntity.ok(
                reservaService.consultar(
                        reservaId,
                        dispositivoId
                )
        );
    }

    /**
     * HU-76.
     *
     * El pasajero declara que no abordo.
     */
    @PostMapping("/{reservaId}/declaracion-no-abordo")
    public ResponseEntity<DetalleReservaResponse> declararNoAbordo(
            @PathVariable Long reservaId,
            @Valid
            @RequestBody
            DeclararNoAbordoRequest peticion
    ) {

        return ResponseEntity.ok(
                reservaService.declararNoAbordo(
                        reservaId,
                        peticion.dispositivoId()
                )
        );
    }
}