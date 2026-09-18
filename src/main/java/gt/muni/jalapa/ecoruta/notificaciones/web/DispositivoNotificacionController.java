package gt.muni.jalapa.ecoruta.notificaciones.web;

import gt.muni.jalapa.ecoruta.notificaciones.servicio.RegistroDeDispositivoService;
import gt.muni.jalapa.ecoruta.notificaciones.web.dto.RegistrarDispositivoRequest;
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

@Tag(name = "Notificaciones — dispositivos",
        description = "El pasajero registra el token FCM de su dispositivo (HU-57)")
@RestController
@RequestMapping("/api/v1/dispositivos")
@RequiredArgsConstructor
public class DispositivoNotificacionController {

    private final RegistroDeDispositivoService dispositivos;

    @Operation(summary = "Registra o actualiza el token de notificaciones")
    @ApiResponse(responseCode = "204", description = "Token guardado")
    @PostMapping("/notificaciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@Valid @RequestBody RegistrarDispositivoRequest peticion) {
        dispositivos.registrar(peticion.dispositivoId(), peticion.tokenNotificacion());
    }
}
