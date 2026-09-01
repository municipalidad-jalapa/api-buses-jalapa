package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.DemandaService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Demanda — reservas", description = "Reserva de lugar en la parada con vigencia y renovacion")
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final DemandaService demandaService;

    @Operation(summary = "Crea una reserva de lugar en una parada",
            description = """
                    Publico: el pasajero es anonimo, se identifica con el id de su
                    dispositivo. La reserva nace ACTIVA y expira a los pocos minutos;
                    ese instante viaja en expiraEn. Una reserva EXPIRADA del mismo
                    dispositivo no impide crear otra.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada"),
            @ApiResponse(responseCode = "404", description = "La parada no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422",
                    description = "El dispositivo ya tiene una reserva vigente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest peticion) {
        ReservaResponse creada = ReservaResponse.de(
                demandaService.crear(peticion.dispositivoId(), peticion.paradaId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Renueva una reserva vigente",
            description = """
                    Extiende la expiracion otros cinco minutos y responde 200 con el
                    nuevo expiraEn. La reserva conserva su identificador y pasa a
                    RENOVADA. Si ya expiro o no esta activa responde 422.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva renovada"),
            @ApiResponse(responseCode = "404", description = "No existe una reserva con ese id",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422",
                    description = "La reserva ya expiro o no esta activa",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/renovacion")
    public ReservaResponse renovar(@PathVariable Long id) {
        return ReservaResponse.de(demandaService.renovar(id));
    }
}
