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

@Tag(name = "Demanda — abordaje", description = "El pasajero indica si subio al bus (HU-57)")
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class AbordajePasajeroController {

    private final AbordajeService abordaje;

    @Operation(summary = "Registra si el pasajero subio",
            description = "Responde al segundo aviso. 422 si la reserva ya no esta activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ABORDO o CANCELADA"),
            @ApiResponse(responseCode = "422", description = "La reserva ya no esta activa",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/abordaje")
    public AbordajeResponse abordar(@PathVariable Long id,
                                    @Valid @RequestBody AbordajeRequest peticion) {
        return abordaje.registrarPasajero(id, peticion.subio());
    }
}
