package gt.muni.jalapa.ecoruta.flota.web;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.flota.web.dto.CrearVehiculoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.EquipoCreadoResponse;
import gt.muni.jalapa.ecoruta.flota.web.dto.ReemplazarEquipoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.VehiculoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flota — vehiculos", description = "Registro de buses y su equipo a bordo")
@RestController
@RequestMapping("/api/v1/admin/vehiculos")
@RequiredArgsConstructor
public class VehiculoAdminController {

    private final VehiculoRepository vehiculos;
    private final EquipoService equipoService;

    @Operation(summary = "Registra un vehiculo",
            description = "Requiere ROLE_ADMIN. Provisional hasta SCRUM-134 (Firebase).")
    @PostMapping
    @Transactional
    public ResponseEntity<VehiculoResponse> crear(@Valid @RequestBody CrearVehiculoRequest peticion) {
        vehiculos.findByIdentificador(peticion.identificador()).ifPresent(existente -> {
            throw new ReglaDeNegocioException(
                    "Ya existe un vehiculo con el identificador " + peticion.identificador());
        });
        if (vehiculos.existsByPlaca(peticion.placa())) {
            throw new ReglaDeNegocioException("Ya existe un vehiculo con la placa " + peticion.placa());
        }

        Vehiculo guardado = vehiculos.save(
                new Vehiculo(peticion.identificador(), peticion.placa()));
        return ResponseEntity.status(HttpStatus.CREATED).body(VehiculoResponse.de(guardado));
    }

    @Operation(summary = "Lista los vehiculos", description = "Requiere ROLE_ADMIN.")
    @GetMapping
    public List<VehiculoResponse> listar() {
        return vehiculos.findAllByOrderByIdentificadorAsc().stream()
                .map(VehiculoResponse::de).toList();
    }

    @Operation(summary = "Cambia el equipo a bordo de un vehiculo",
            description = """
                    Revoca el equipo activo y emite otro, en una sola transaccion.
                    Es el tramite de cambiar el hardware (SCRUM-143): el historico del
                    vehiculo no se toca, las posiciones ya escritas conservan su
                    atribucion. La credencial nueva se devuelve UNA sola vez.""")
    @PostMapping("/{vehiculoId}/equipos")
    public ResponseEntity<EquipoCreadoResponse> reemplazarEquipo(
            @PathVariable Long vehiculoId,
            @Valid @RequestBody ReemplazarEquipoRequest peticion) {

        AltaDeEquipo alta = equipoService.reemplazarEquipoDe(vehiculoId, peticion.etiqueta());
        return ResponseEntity.status(HttpStatus.CREATED).body(EquipoCreadoResponse.de(alta));
    }
}
