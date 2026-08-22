package gt.muni.jalapa.ecoruta.flota.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.flota.web.dto.EmitirEquipoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.EquipoCreadoResponse;
import gt.muni.jalapa.ecoruta.flota.web.dto.EquipoResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flota — equipos", description = "Alta y revocacion de credenciales del equipo a bordo")
@RestController
@RequestMapping("/api/v1/admin/equipos")
@RequiredArgsConstructor
public class EquipoAdminController {

    private final EquipoService equipoService;

    @Operation(summary = "Emite una credencial para un equipo a bordo",
            description = """
                    Requiere ROLE_ADMIN. La credencial se devuelve UNA sola vez: no se
                    persiste en claro ni se puede volver a consultar. Si se pierde, hay
                    que revocar el equipo y emitir otro.

                    El mecanismo de autenticacion de administradores es provisional
                    hasta SCRUM-134 (Firebase).""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credencial emitida"),
            @ApiResponse(responseCode = "422",
                    description = "El vehiculo ya tiene un equipo activo",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EquipoCreadoResponse> emitir(@Valid @RequestBody EmitirEquipoRequest peticion) {
        AltaDeEquipo alta = equipoService.emitir(peticion.vehiculoId(), peticion.etiqueta());
        return ResponseEntity.status(HttpStatus.CREATED).body(EquipoCreadoResponse.de(alta));
    }

    @Operation(summary = "Lista los equipos registrados",
            description = "Requiere ROLE_ADMIN. Nunca devuelve el secreto ni su hash.")
    @GetMapping
    public List<EquipoResponse> listar() {
        return equipoService.listar().stream().map(EquipoResponse::de).toList();
    }

    @Operation(summary = "Revoca la credencial de un equipo",
            description = """
                    Requiere ROLE_ADMIN. Surte efecto de inmediato: la siguiente peticion
                    de ingesta con esa credencial se rechaza, sin esperar expiracion.""")
    @ApiResponse(responseCode = "204", description = "Equipo revocado")
    @PostMapping("/{equipoId}/revocacion")
    public ResponseEntity<Void> revocar(@PathVariable Long equipoId) {
        equipoService.revocar(equipoId);
        return ResponseEntity.noContent().build();
    }
}
