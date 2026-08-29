package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.demanda.servicio.RegistroDeEsperaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearRegistroRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.RegistroCreadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Demanda — registros",
        description = "Alta minima de espera para poder demostrar HU-57")
@RestController
@RequestMapping("/api/v1/demanda/registros")
@RequiredArgsConstructor
public class RegistroDeEsperaController {

    private final RegistroDeEsperaService registros;

    @Operation(summary = "Crea un registro de espera activo")
    @ApiResponse(responseCode = "201", description = "Reserva ACTIVA")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroCreadoResponse crear(@Valid @RequestBody CrearRegistroRequest peticion) {
        return RegistroCreadoResponse.de(
                registros.registrar(peticion.dispositivoId(), peticion.paradaId()));
    }
}
