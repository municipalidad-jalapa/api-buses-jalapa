package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ConsultaDemandaRepository;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ciclo de vida de una reserva de lugar en la parada (Desarrollo-135): creacion
 * con vigencia de pocos minutos, renovacion mientras siga vigente y expiracion
 * automatica de las vencidas.
 *
 * <p>La vigencia se calcula aqui, con {@link DemandaProperties#vigencia()}, tanto
 * al crear como al renovar. Se usa {@link Instant#now()} y no un {@code Clock}
 * inyectado porque el resto del proyecto hace lo mismo; las pruebas mueven el
 * tiempo tocando {@code expira_en} en la base.
 *
 * <p>Consulta de demanda vigente por parada (SCRUM-275).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandaService {

    private final ReservaRepository reservas;
    private final ParadaRepository paradas;
    private final DemandaProperties propiedades;
    private final ConsultaDemandaRepository consulta;

    /**
     * Crea una reserva ACTIVA que expira {@code vigenciaMinutos} despues.
     *
     * @throws RecursoNoEncontradoException si la parada no existe
     * @throws ReglaDeNegocioException      si el dispositivo ya tiene una reserva
     *                                      que ocupa el cupo (una EXPIRADA no cuenta)
     */
    @Transactional
    public Reserva crear(String dispositivoId, Long paradaId) {
        // Se busca el objeto Parada completo para pasarlo a la Reserva
        Parada parada = paradas.findById(paradaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", paradaId));

        // Se utilizan los estados explícitos en lugar de OCUPAN_CUPO
        if (reservas.existsByDispositivoIdAndEstadoIn(dispositivoId, List.of(EstadoReserva.ACTIVA, EstadoReserva.RENOVADA))) {
            throw new ReglaDeNegocioException(
                    "El dispositivo ya tiene una reserva vigente en una parada.");
        }
        Instant ahora = Instant.now();
        Reserva reserva = reservas.save(
                new Reserva(dispositivoId, parada, ahora.plus(propiedades.vigencia())));
        log.info("Reserva {} creada para la parada {}; expira {}",
                reserva.getId(), paradaId, reserva.getExpiraEn());
        return reserva;
    }

    /**
     * Renueva una reserva vigente: extiende su expiracion
     * {@code renovacionMinutos} y la deja RENOVADA, conservando su identificador.
     *
     * @throws RecursoNoEncontradoException si no existe una reserva con ese id
     * @throws ReglaDeNegocioException      si la reserva ya expiro o no esta activa (422)
     */
    @Transactional
    public Reserva renovar(Long reservaId) {
        Reserva reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva", reservaId));

        Instant ahora = Instant.now();
        if (!reserva.estaVigente(ahora)) {
            throw new ReglaDeNegocioException(
                    "La reserva %d ya expiro o no esta activa; no se puede renovar."
                            .formatted(reservaId));
        }

        reserva.renovar(ahora.plus(propiedades.renovacion()));
        log.info("Reserva {} renovada; nueva expiracion {}", reservaId, reserva.getExpiraEn());
        return reserva;
    }

    /**
     * Marca EXPIRADA toda reserva vigente cuya vigencia ya vencio. Idempotente:
     * si no hay vencidas no toca nada. Lo llama la tarea programada.
     *
     * @return cuantas reservas se expiraron en esta pasada
     */
    @Transactional
    public int expirarVencidas() {
        // Se reemplaza RENOVABLES por la lista directa para evitar problemas de compilacion
        int cuantas = reservas.marcarExpiradas(List.of(EstadoReserva.ACTIVA, EstadoReserva.RENOVADA), Instant.now());
        if (cuantas > 0) {
            log.info("Reservas expiradas por vencimiento: {}", cuantas);
        }
        return cuantas;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> contarReservasActivasPorParada(Collection<Long> paradaIds) {
        return consulta.contarReservasActivasPorParada(paradaIds);
    }
}