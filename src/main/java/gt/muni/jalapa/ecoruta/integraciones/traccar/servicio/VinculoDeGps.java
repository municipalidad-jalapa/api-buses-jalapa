package gt.muni.jalapa.ecoruta.integraciones.traccar.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.integraciones.traccar.dominio.DispositivoExterno;
import gt.muni.jalapa.ecoruta.integraciones.traccar.repositorio.DispositivoExternoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pone o quita el GPS (el uniqueId de Traccar, normalmente el IMEI) de un bus
 * desde el panel municipal, sin tocar la base a mano.
 *
 * <p>La recepcion de Traccar resuelve IMEI -> equipo ACTIVO -> vehiculo. Si el
 * bus aun no tiene equipo se emite uno aqui mismo; su credencial se descarta,
 * porque el GPS habla con Traccar y nunca la presenta. Emitir credenciales que
 * alguien vaya a usar sigue siendo cosa del SuperAdmin (/admin/equipos).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VinculoDeGps {

    static final String ETIQUETA = "GPS Traccar";

    private final DispositivoExternoRepository dispositivos;
    private final EquipoRepository equipos;
    private final VehiculoRepository vehiculos;
    private final EquipoService equipoService;

    /** Un GPS por bus: si el bus ya tenia otro, se reemplaza. */
    @Transactional
    public void vincular(Long vehiculoId, String gps) {
        Vehiculo vehiculo = vehiculos.findById(vehiculoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo", vehiculoId));
        String identificador = gps.strip();

        Equipo equipo = equipos.findByVehiculoIdAndEstado(vehiculoId, EstadoEquipo.ACTIVO)
                .orElseGet(() -> equipos.getReferenceById(
                        equipoService.emitir(vehiculoId, ETIQUETA).equipoId()));

        Optional<DispositivoExterno> existente = dispositivos.buscar(DispositivoExterno.TRACCAR, identificador);
        existente.map(DispositivoExterno::getEquipo)
                .filter(Equipo::estaActivo)
                .map(Equipo::getVehiculo)
                .filter(otro -> otro != null && !otro.getId().equals(vehiculoId))
                .ifPresent(otro -> {
                    throw new ReglaDeNegocioException(
                            "El GPS %s ya esta en el %s. Quitaselo a ese bus primero."
                                    .formatted(identificador, otro.getIdentificador()));
                });

        dispositivos.findByProveedorAndEquipoId(DispositivoExterno.TRACCAR, equipo.getId()).stream()
                .filter(d -> !d.getIdentificador().equals(identificador))
                .forEach(dispositivos::delete);

        DispositivoExterno dispositivo = existente.orElseGet(DispositivoExterno::new);
        dispositivo.setIdentificador(identificador);
        dispositivo.setEquipo(equipo);
        dispositivos.save(dispositivo);
        log.info("GPS {} vinculado al vehiculo {}", identificador, vehiculo.getIdentificador());
    }

    @Transactional
    public void desvincular(Long vehiculoId) {
        Vehiculo vehiculo = vehiculos.findById(vehiculoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo", vehiculoId));
        equipos.findByVehiculoIdAndEstado(vehiculoId, EstadoEquipo.ACTIVO)
                .map(equipo -> dispositivos.findByProveedorAndEquipoId(DispositivoExterno.TRACCAR, equipo.getId()))
                .ifPresent(lista -> {
                    dispositivos.deleteAll(lista);
                    log.info("GPS quitado del vehiculo {}", vehiculo.getIdentificador());
                });
    }

    @Transactional(readOnly = true)
    public Optional<String> gpsDe(Long vehiculoId) {
        return Optional.ofNullable(gpsPorVehiculo().get(vehiculoId));
    }

    /** vehiculoId -> GPS vigente. Los buses sin GPS no aparecen. */
    @Transactional(readOnly = true)
    public Map<Long, String> gpsPorVehiculo() {
        return dispositivos.vigentes(DispositivoExterno.TRACCAR).stream()
                .collect(Collectors.toMap(d -> d.getEquipo().getVehiculo().getId(),
                        DispositivoExterno::getIdentificador, (uno, otro) -> uno));
    }
}
