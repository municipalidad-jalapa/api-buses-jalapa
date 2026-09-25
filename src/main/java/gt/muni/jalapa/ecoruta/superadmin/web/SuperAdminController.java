package gt.muni.jalapa.ecoruta.superadmin.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.superadmin.servicio.AdministracionDelSistema;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.CrearCuentaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.CuentaResponse;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.EditarCuentaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.GuardarParadaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.GuardarRutaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.ParadaResponse;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.RutaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administracion del sistema (SCRUM-26, bloque D). Todo aqui exige rol
 * SuperAdmin: la regla vive en {@code SecurityConfig} sobre
 * {@code /api/v1/superadmin/**}, asi que una cuenta de municipalidad, un piloto
 * o un pasajero reciben 403.
 */
@Tag(name = "SuperAdmin", description = "Cuentas, rutas y paradas (SCRUM-26, bloque D)")
@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "403", description = "La cuenta no es SuperAdmin",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class SuperAdminController {

    private final AdministracionDelSistema administracion;

    @Operation(summary = "Lista las cuentas de operacion")
    @GetMapping("/cuentas")
    public List<CuentaResponse> cuentas() {
        return administracion.cuentas();
    }

    @Operation(summary = "Crea una cuenta de piloto, municipalidad o SuperAdmin",
            description = """
                    Nunca viaja una contrasena: la identidad vive en Firebase y aqui
                    se guarda el uid con el que se enlaza la cuenta. Sin uid la cuenta
                    existe pero todavia no puede iniciar sesion.""")
    @PostMapping("/cuentas")
    public ResponseEntity<CuentaResponse> crearCuenta(@RequestBody CrearCuentaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administracion.crearCuenta(peticion));
    }

    @Operation(summary = "Edita el rol, la ruta, el uid o el estado de una cuenta")
    @PatchMapping("/cuentas/{id}")
    public CuentaResponse editarCuenta(@PathVariable Long id,
                                       @RequestBody EditarCuentaRequest peticion) {
        return administracion.editarCuenta(id, peticion);
    }

    @Operation(summary = "Desactiva una cuenta",
            description = "No se borra: deja de entrar y conserva su historial.")
    @PostMapping("/cuentas/{id}/desactivacion")
    public CuentaResponse desactivarCuenta(@PathVariable Long id) {
        return administracion.desactivarCuenta(id);
    }

    @Operation(summary = "Lista las rutas")
    @GetMapping("/rutas")
    public List<RutaResponse> rutas() {
        return administracion.rutas();
    }

    @Operation(summary = "Crea una ruta")
    @PostMapping("/rutas")
    public ResponseEntity<RutaResponse> crearRuta(@RequestBody GuardarRutaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administracion.crearRuta(peticion));
    }

    @Operation(summary = "Edita una ruta")
    @PatchMapping("/rutas/{id}")
    public RutaResponse editarRuta(@PathVariable Long id, @RequestBody GuardarRutaRequest peticion) {
        return administracion.editarRuta(id, peticion);
    }

    @Operation(summary = "Crea una parada en una ruta")
    @PostMapping("/paradas")
    public ResponseEntity<ParadaResponse> crearParada(@RequestBody GuardarParadaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administracion.crearParada(peticion));
    }

    @Operation(summary = "Edita una parada")
    @PatchMapping("/paradas/{id}")
    public ParadaResponse editarParada(@PathVariable Long id,
                                       @RequestBody GuardarParadaRequest peticion) {
        return administracion.editarParada(id, peticion);
    }
}
