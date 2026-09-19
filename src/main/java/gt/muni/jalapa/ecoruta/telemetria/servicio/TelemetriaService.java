package gt.muni.jalapa.ecoruta.telemetria.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoAutenticado;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import gt.muni.jalapa.ecoruta.telemetria.repositorio.PosicionHistoricaRepository;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.LoteAceptadoResponse;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionRequest;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Ingesta y consulta de posiciones (SCRUM-138 y SCRUM-139). */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetriaService {

    private final PosicionHistoricaRepository posiciones;
    private final EquipoRepository equipos;
    private final VehiculoRepository vehiculos;
    private final TelemetriaProperties propiedades;
    private final ApplicationEventPublisher eventos;
    private final MeterRegistry metricas;

    // Epoch-seconds del ultimo lote recibido, sin importar cuantas lecturas
    // se descartaron. SCRUM-151 (HU-56, AC3) alerta si esto no avanza durante
    // el horario de operacion.
    private final AtomicLong ultimaIngestaEpochSegundos = new AtomicLong(0);

    @PostConstruct
    void registrarMetricaDeIngesta() {
        Gauge.builder("ecoruta_telemetria_ultima_recepcion_timestamp_seconds",
                        ultimaIngestaEpochSegundos, AtomicLong::get)
                .description("Epoch-seconds del ultimo lote de telemetria recibido")
                .register(metricas);
    }

    /**
     * Recibe un lote de posiciones de un equipo ya autenticado.
     *
     * <p>El vehiculo sale del principal, nunca del cuerpo: por eso un equipo no
     * puede reportar posiciones a nombre de otro bus.
     *
     * <p>Las lecturas con el reloj fuera de la ventana de tolerancia se descartan
     * en silencio y el lote se acepta igual. Rechazar el lote entero no ayudaria:
     * el equipo no puede corregir su reloj a partir del rechazo y reintentaria en
     * bucle, perdiendo tambien las lecturas buenas.
     */
    @Transactional
    public LoteAceptadoResponse ingestar(EquipoAutenticado autenticado,
                                         List<PosicionRequest> lote) {
        if (!autenticado.tieneVehiculoAsignado()) {
            throw new ReglaDeNegocioException(
                    "El equipo no tiene un vehiculo asignado: no se puede atribuir la posicion.");
        }

        Equipo equipo = equipos.findById(autenticado.equipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo", autenticado.equipoId()));

        return registrar(equipo, lote.stream()
                .map(p -> new LecturaEntrante(p.latitud(), p.longitud(), p.velocidadKmh(), p.timestamp(), null))
                .toList());
    }

    /**
     * Nucleo de la ingesta, compartido por el equipo a bordo y por las
     * integraciones (SCRUM-24, Traccar). Quien llama ya resolvio el equipo.
     *
     * <p>Descarta, sin rechazar el lote, las lecturas con el reloj fuera de la
     * ventana y las que ya se registraron con la misma clave de origen. El evento
     * de posicion vigente se publica dentro de la transaccion y sus oyentes son
     * AFTER_COMMIT: solo sale si todo se guardo.
     */
    @Transactional
    public LoteAceptadoResponse registrar(Equipo equipo, List<LecturaEntrante> lote) {
        if (equipo.getVehiculo() == null) {
            throw new ReglaDeNegocioException(
                    "El equipo no tiene un vehiculo asignado: no se puede atribuir la posicion.");
        }
        Instant ahora = Instant.now();
        ultimaIngestaEpochSegundos.set(ahora.getEpochSecond());

        List<LecturaEntrante> enVentana = lote.stream()
                .filter(lectura -> dentroDeLaVentana(lectura.registradoEn(), ahora))
                .toList();
        int fueraDeVentana = lote.size() - enVentana.size();
        if (fueraDeVentana > 0) {
            log.warn("Posicion descartada por timestamp fuera de ventana: equipo={} descartadas={}",
                    equipo.getCodigoPublico(), fueraDeVentana);
        }

        // Un reenvio repetido no duplica: ni contra lo ya guardado ni dentro del
        // mismo lote. El indice uq_posicion_clave_origen lo garantiza en la base.
        Set<String> clavesVistas = new HashSet<>();
        List<LecturaEntrante> aceptables = enVentana.stream()
                .filter(lectura -> lectura.claveOrigen() == null
                        || (clavesVistas.add(lectura.claveOrigen())
                            && !posiciones.existsByClaveOrigen(lectura.claveOrigen())))
                .toList();

        aceptables.forEach(lectura -> posiciones.save(new PosicionHistorica(
                Geo.punto(lectura.latitud(), lectura.longitud()),
                lectura.velocidadKmh(),
                lectura.registradoEn(),
                equipo,
                equipo.getVehiculo(),
                lectura.claveOrigen())));

        equipo.setUltimoUsoEn(ahora);

        // La posicion vigente es la de timestamp mas reciente del lote, no la
        // ultima del array: un lote acumulado sin cobertura llega desordenado.
        Long vehiculoId = equipo.getVehiculo().getId();
        aceptables.stream()
                .max(Comparator.comparing(LecturaEntrante::registradoEn))
                .flatMap(masReciente -> posiciones.findFirstByVehiculoIdOrderByRegistradoEnDescIdDesc(vehiculoId))
                // El DTO se arma AQUI, dentro de la transaccion: es el unico punto
                // donde el vehiculo LAZY todavia se puede navegar. Quien escucha el
                // evento lo hace despues del commit, sin sesion.
                .map(vigente -> PosicionActualResponse.de(vigente, equipo.getVehiculo().getIdentificador(),
                        equipo.getVehiculo().getRutaId()))
                .ifPresent(vigente -> eventos.publishEvent(new PosicionVigenteActualizada(vigente)));

        return new LoteAceptadoResponse(lote.size(), aceptables.size(), lote.size() - aceptables.size());
    }

    /** Simetrica, tal como la fija SCRUM-138. */
    private boolean dentroDeLaVentana(Instant registradoEn, Instant ahora) {
        Duration desfase = Duration.between(registradoEn, ahora).abs();
        return desfase.compareTo(propiedades.ventana()) <= 0;
    }

    @Transactional(readOnly = true)
    public Optional<PosicionActualResponse> posicionVigente(Long vehiculoId) {
        Optional<PosicionHistorica> vigente = vehiculoId == null
                ? posiciones.findFirstByOrderByRegistradoEnDescIdDesc()
                : posiciones.findFirstByVehiculoIdOrderByRegistradoEnDescIdDesc(vehiculoId);

        // El DTO se arma aqui dentro: open-in-view esta en false y el vehiculo es
        // una relacion LAZY que fuera de la transaccion ya no se puede navegar.
        return vigente.map(posicion -> PosicionActualResponse.de(posicion,
                posicion.getVehiculo() != null
                        ? posicion.getVehiculo().getIdentificador()
                        : null,
                posicion.getVehiculo() != null
                        ? posicion.getVehiculo().getRutaId()
                        : null));
    }

    /**
     * La posicion vigente del bus que recorre una ruta (SCRUM-166: un bus por
     * ruta). Vacio si la ruta no tiene bus activo o si su bus aun no reporta:
     * nunca se devuelve la posicion de un bus de otra ruta.
     */
    @Transactional(readOnly = true)
    public Optional<PosicionActualResponse> posicionVigentePorRuta(Long rutaId) {
        return vehiculos.findFirstByRutaIdAndActivoTrue(rutaId)
                .flatMap(vehiculo -> posicionVigente(vehiculo.getId()));
    }
}
