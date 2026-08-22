package gt.muni.jalapa.ecoruta.flota.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.seguridad.TokenDeEquipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Alta, revocacion y autenticacion de los equipos a bordo (SCRUM-142, SCRUM-143). */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipoService {

    /**
     * Hash de un valor constante, usado como senuelo.
     *
     * <p>Cuando el codigo publico no existe o el equipo esta revocado igual se
     * corre un bcrypt contra este hash, para que el tiempo de respuesta no
     * distinga "no existe ese codigo" de "el secreto no coincide".
     */
    private static final String HASH_SENUELO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final EquipoRepository equipos;
    private final VehiculoRepository vehiculos;
    private final GeneradorDeCredenciales generador;
    private final PasswordEncoder passwordEncoder;

    /**
     * Autentica al equipo que presenta un token.
     *
     * <p>Va a la base en CADA peticion, a proposito: es lo que hace que revocar
     * una credencial surta efecto de inmediato (SCRUM-142). No agregar cache
     * aqui -- una cache es justamente lo que ese criterio prohibe.
     */
    @Transactional(readOnly = true)
    public Optional<EquipoAutenticado> autenticar(TokenDeEquipo token) {
        Optional<Equipo> fila = equipos.findByCodigoPublico(token.codigoPublico());

        if (fila.isEmpty()) {
            passwordEncoder.matches(token.secreto(), HASH_SENUELO);
            return rechazar(token, "NO_EXISTE");
        }

        Equipo equipo = fila.get();
        if (!equipo.estaActivo()) {
            passwordEncoder.matches(token.secreto(), HASH_SENUELO);
            return rechazar(token, "REVOCADO");
        }

        // BCryptPasswordEncoder compara con equalsNoEarlyReturn: la comparacion en
        // tiempo constante la da la libreria. No escribir una a mano ni meter un
        // equals() como atajo.
        if (!passwordEncoder.matches(token.secreto(), equipo.getSecretoHash())) {
            return rechazar(token, "SECRETO_NO_COINCIDE");
        }

        log.debug("Credencial de equipo aceptada: codigo={}", equipo.getCodigoPublico());
        return Optional.of(EquipoAutenticado.de(equipo));
    }

    private Optional<EquipoAutenticado> rechazar(TokenDeEquipo token, String motivo) {
        // Solo el codigo publico y el motivo. El secreto no se registra nunca, ni
        // truncado: para eso existe el token de dos partes (criterio (d)).
        log.warn("Credencial de equipo rechazada: codigo={} motivo={}",
                token.codigoPublico(), motivo);
        return Optional.empty();
    }

    /** Deja constancia del ultimo uso. Lo llama la ingesta, no el filtro. */
    @Transactional
    public void registrarUso(Long equipoId, Instant cuando) {
        equipos.findById(equipoId).ifPresent(equipo -> equipo.setUltimoUsoEn(cuando));
    }

    /**
     * Emite una credencial nueva para un vehiculo.
     *
     * @return el alta con el secreto en claro; es la unica vez que existe
     */
    @Transactional
    public AltaDeEquipo emitir(Long vehiculoId, String etiqueta) {
        Vehiculo vehiculo = vehiculos.findById(vehiculoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo", vehiculoId));

        equipos.findByVehiculoIdAndEstado(vehiculoId, EstadoEquipo.ACTIVO)
                .ifPresent(activo -> {
                    throw new ReglaDeNegocioException(
                            "El vehiculo %s ya tiene un equipo activo. Revoquelo antes de emitir otro."
                                    .formatted(vehiculo.getIdentificador()));
                });

        AltaDeEquipo alta = guardar(vehiculo, etiqueta);
        log.info("Credencial emitida para el vehiculo {}: codigo={}",
                vehiculo.getIdentificador(), alta.credencial().codigoPublico());
        return alta;
    }

    /**
     * Crea la fila y devuelve todo lo que la capa web necesita, resuelto aqui
     * dentro: con open-in-view en false, fuera de la transaccion no hay sesion y
     * navegar el vehiculo LAZY reventaria.
     */
    private AltaDeEquipo guardar(Vehiculo vehiculo, String etiqueta) {
        CredencialEmitida credencial = generador.generar();
        Equipo equipo = equipos.save(new Equipo(credencial.codigoPublico(),
                credencial.secretoHash(), etiqueta, vehiculo));
        return new AltaDeEquipo(equipo.getId(), credencial, etiqueta,
                vehiculo.getIdentificador());
    }

    /**
     * Revoca un equipo y emite otro para el mismo vehiculo, en una sola
     * transaccion.
     *
     * <p>Es el tramite de "cambiar el equipo fisico" de SCRUM-143. El historico
     * del vehiculo no se toca: las posiciones ya escritas conservan su vehiculo_id
     * y su equipo_id.
     */
    @Transactional
    public AltaDeEquipo reemplazarEquipoDe(Long vehiculoId, String etiqueta) {
        Vehiculo vehiculo = vehiculos.findById(vehiculoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo", vehiculoId));

        equipos.findByVehiculoIdAndEstado(vehiculoId, EstadoEquipo.ACTIVO)
                .ifPresent(activo -> {
                    activo.revocar(Instant.now());
                    // El flush explicito importa: uq_equipo_activo_por_vehiculo es un
                    // indice unico parcial, y sin el la insercion del nuevo equipo
                    // puede llegar a la base antes que la revocacion del viejo.
                    equipos.saveAndFlush(activo);
                    log.info("Equipo {} revocado por reemplazo en el vehiculo {}",
                            activo.getCodigoPublico(), vehiculo.getIdentificador());
                });

        return guardar(vehiculo, etiqueta);
    }

    @Transactional
    public void revocar(Long equipoId) {
        Equipo equipo = equipos.findById(equipoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo", equipoId));
        equipo.revocar(Instant.now());
        log.info("Equipo revocado: codigo={}", equipo.getCodigoPublico());
    }

    @Transactional(readOnly = true)
    public List<Equipo> listar() {
        return equipos.listarConVehiculo();
    }
}
