package gt.muni.jalapa.ecoruta.flota.web;

import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.flota.web.dto.AsignarVehiculoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.CrearVehiculoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.EquipoCreadoResponse;
import gt.muni.jalapa.ecoruta.flota.web.dto.ReemplazarEquipoRequest;
import gt.muni.jalapa.ecoruta.flota.web.dto.VehiculoResponse;
import gt.muni.jalapa.ecoruta.flota.web.dto.VincularGpsRequest;
import gt.muni.jalapa.ecoruta.integraciones.traccar.servicio.VinculoDeGps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Flota — vehiculos", description = "Registro de buses y su equipo a bordo")
@RestController
@RequestMapping("/api/v1/admin/vehiculos")
@RequiredArgsConstructor
public class VehiculoAdminController {

    private final VehiculoRepository vehiculos;
    private final EquipoService equipoService;
    private final RutaRepository rutas;
    private final VinculoDeGps vinculoDeGps;

    @Operation(summary = "Registra un vehiculo",
            description = "Cualquier admin del panel municipal. Ruta, capacidad y GPS son opcionales.")
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

        Vehiculo nuevo = new Vehiculo(peticion.identificador().strip(), peticion.placa().strip());
        nuevo.setCapacidad(peticion.capacidad());
        asignarRuta(nuevo, peticion.rutaId());
        Vehiculo guardado = vehiculos.save(nuevo);
        if (peticion.gps() != null && !peticion.gps().isBlank()) {
            vinculoDeGps.vincular(guardado.getId(), peticion.gps());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(guardado));
    }

    @Operation(summary = "Cambia la ruta y la capacidad de un vehiculo",
            description = "rutaId null lo deja sin ruta. Cada ruta tiene un solo bus activo.")
    @PutMapping("/{vehiculoId}/asignacion")
    @Transactional
    public VehiculoResponse asignar(@PathVariable Long vehiculoId,
                                    @Valid @RequestBody AsignarVehiculoRequest peticion) {
        Vehiculo vehiculo = vehiculos.findById(vehiculoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo", vehiculoId));
        vehiculo.setCapacidad(peticion.capacidad());
        asignarRuta(vehiculo, peticion.rutaId());
        return respuesta(vehiculos.save(vehiculo));
    }

    @Operation(summary = "Pone o cambia el GPS de un vehiculo",
            description = """
                    gps es el uniqueId del dispositivo en Traccar (normalmente el IMEI).
                    Si el bus no tiene equipo a bordo se emite uno; un GPS por bus y un
                    bus por GPS. Traccar tiene que conocer el dispositivo
                    (alta en Traccar o database.registerUnknown).""")
    @PutMapping("/{vehiculoId}/gps")
    @Transactional
    public VehiculoResponse vincularGps(@PathVariable Long vehiculoId,
                                        @Valid @RequestBody VincularGpsRequest peticion) {
        vinculoDeGps.vincular(vehiculoId, peticion.gps());
        return respuesta(vehiculos.findById(vehiculoId).orElseThrow());
    }

    @Operation(summary = "Quita el GPS de un vehiculo",
            description = "Traccar deja de poder reportar posiciones de este bus.")
    @DeleteMapping("/{vehiculoId}/gps")
    @Transactional
    public VehiculoResponse desvincularGps(@PathVariable Long vehiculoId) {
        vinculoDeGps.desvincular(vehiculoId);
        return respuesta(vehiculos.findById(vehiculoId).orElseThrow());
    }

    private VehiculoResponse respuesta(Vehiculo vehiculo) {
        return VehiculoResponse.de(vehiculo, vinculoDeGps.gpsDe(vehiculo.getId()).orElse(null));
    }

    /** Una ruta, un bus activo (uq_vehiculo_activo_por_ruta): se avisa antes de chocar. */
    private void asignarRuta(Vehiculo vehiculo, Long rutaId) {
        if (rutaId != null) {
            if (!rutas.existsById(rutaId)) {
                throw new ReglaDeNegocioException("La ruta no existe.");
            }
            vehiculos.findFirstByRutaIdAndActivoTrue(rutaId)
                    .filter(otro -> !otro.getId().equals(vehiculo.getId()))
                    .ifPresent(otro -> {
                        throw new ReglaDeNegocioException(
                                "La ruta ya tiene al " + otro.getIdentificador()
                                        + ". Quitale la ruta a ese bus primero.");
                    });
        }
        vehiculo.setRutaId(rutaId);
    }

    @Operation(summary = "Lista los vehiculos", description = "Requiere ROLE_ADMIN.")
    @GetMapping
    public List<VehiculoResponse> listar() {
        Map<Long, String> gps = vinculoDeGps.gpsPorVehiculo();
        return vehiculos.findAllByOrderByIdentificadorAsc().stream()
                .map(v -> VehiculoResponse.de(v, gps.get(v.getId()))).toList();
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
