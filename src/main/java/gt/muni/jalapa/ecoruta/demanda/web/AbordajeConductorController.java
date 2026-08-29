package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.AbordajeService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AbordajeRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AbordajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Conductor — abordaje",
        description = "El dato del conductor sobrescribe al del pasajero (HU-57)")
@RestController
@RequestMapping("/api/v1/conductor/reservas")
@RequiredArgsConstructor
public class AbordajeConductorController {

    private final AbordajeService abordaje;

    @Operation(summary = "El conductor registra el abordaje",
            description = "Si el pasajero ya habia respondido, este dato gana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ABORDO o CANCELADA"),
            @ApiResponse(responseCode = "401", description = "Sin JWT de conductor",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "La reserva no admite correccion",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/abordaje")
    public AbordajeResponse abordar(@PathVariable Long id,
                                    @Valid @RequestBody AbordajeRequest peticion) {
        return abordaje.registrarConductor(id, peticion.subio());
    }
}
