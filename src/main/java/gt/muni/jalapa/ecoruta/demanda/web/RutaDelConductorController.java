package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.demanda.servicio.RutaDelConductorService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ElegirRutaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.RutaDelConductorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La ruta que maneja el conductor. La regla {@code /api/v1/conductor/**} exige rol CONDUCTOR. */
@Tag(name = "Conductor — ruta", description = "Elegir o cambiar la ruta que se maneja")
@RestController
@RequestMapping("/api/v1/conductor/ruta")
@RequiredArgsConstructor
public class RutaDelConductorController {

    private final RutaDelConductorService servicio;

    @Operation(summary = "La ruta del conductor y las que puede elegir",
            description = "rutaId es null si todavia no eligio ninguna. Solo se listan rutas publicadas.")
    @GetMapping
    public RutaDelConductorResponse consultar(Authentication autenticacion) {
        return servicio.consultar(autenticacion.getName());
    }

    @Operation(summary = "Elige la ruta que va a manejar",
            description = "Vale de inmediato para el panel, la atencion de paradas, el abordaje y los atrasos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La ruta elegida"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es un piloto activo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "La ruta no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping
    public RutaDelConductorResponse elegir(@Valid @RequestBody ElegirRutaRequest peticion,
                                           Authentication autenticacion) {
        return servicio.elegir(autenticacion.getName(), peticion.rutaId());
    }
}
