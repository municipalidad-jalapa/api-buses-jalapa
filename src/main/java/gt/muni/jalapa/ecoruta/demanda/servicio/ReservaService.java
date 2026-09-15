package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Ciclo de vida de la reserva de espera en parada: la crea (SCRUM-306), la
 * renueva y la expira cuando vence (HU-135).
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    /** Solo estos estados bloquean una reserva nueva. ABORDO no es vigente. */
    static final Set<EstadoReserva> ESTADOS_VIGENTES = EstadoReserva.RENOVABLES;

    private static final String INDICE_VIGENTE = "uq_registro_activo_por_dispositivo";

    private final ReservaRepository reservas;
    private final ParadaRepository paradas;
    private final DemandaProperties demanda;
    private final Clock reloj;

    @Transactional
    public ReservaResponse crear(CrearReservaRequest peticion) {
        Parada parada = paradas.findById(peticion.paradaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", peticion.paradaId()));

        if (!paradas.estaDentroDeGeocerca(
                parada.getId(), peticion.latitud(), peticion.longitud(), demanda.geocercaMetros())) {
            throw new ReglaDeNegocioException(
                    "Debes acercarte más a la parada para registrar que estás esperando.");
        }

        if (reservas.existeVigentePorDispositivo(peticion.dispositivoId(), ESTADOS_VIGENTES)) {
            throw new ReglaDeNegocioException("Este dispositivo ya tiene una reserva activa.");
        }

        Instant ahora = Instant.now(reloj);
        Reserva reserva = new Reserva(
                peticion.dispositivoId(),
                parada,
                EstadoReserva.ACTIVA,
                ahora,
                ahora.plus(demanda.ttl()));

        try {
            // saveAndFlush fuerza el INSERT ahora: el indice parcial es la garantia
            // final bajo solicitudes concurrentes.
            Reserva guardada = reservas.saveAndFlush(reserva);
            return ReservaResponse.de(guardada);
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionDeReservaVigente(ex)) {
                throw new ReglaDeNegocioException("Este dispositivo ya tiene una reserva activa.");
            }
            throw ex;
        }
    }

    /**
     * Extiende la vigencia de una reserva vigente otro periodo completo (HU-135).
     * Conserva su identificador y pasa a RENOVADA.
     *
     * <p>Solo el dispositivo que la creo puede renovarla, con el mismo criterio
     * que la cancelacion: sin ese control cualquiera podria mantener vivas
     * reservas ajenas e inflar la demanda que ve el conductor.
     *
     * @throws RecursoNoEncontradoException si no existe
     * @throws AccessDeniedException        si es de otro dispositivo (403)
     * @throws ReglaDeNegocioException      si ya vencio o no esta vigente
     */
    @Transactional
    public ReservaResponse renovar(Long reservaId, String dispositivoId) {
        Reserva reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva", reservaId));

        if (!reserva.perteneceA(dispositivoId)) {
            throw new AccessDeniedException("Esta reserva no pertenece a este dispositivo.");
        }

        Instant ahora = Instant.now(reloj);
        if (!reserva.estaVigente(ahora)) {
            throw new ReglaDeNegocioException("Esta reserva ya venció o no está activa.");
        }

        reserva.renovar(ahora.plus(demanda.ttl()));
        return ReservaResponse.de(reserva);
    }

    /**
     * El pasajero suelta su reserva a mano (HU-124 / SCRUM-172). No se borra:
     * pasa a CANCELADA y guarda cuando se cancelo.
     *
     * <p>Solo el dispositivo que la creo puede cancelarla. Sin ese control,
     * cualquiera podria soltar la reserva de otro probando ids, que son
     * secuenciales.
     *
     * <p>Una reserva en {@link EstadoReserva#ABORDO} jamás pasa a CANCELADA por
     * este flujo: el pasajero ya subió y el conteo de espera ya no la incluye.
     *
     * @throws RecursoNoEncontradoException si no existe
     * @throws AccessDeniedException        si es de otro dispositivo (403)
     * @throws ReglaDeNegocioException      si ya estaba cancelada, abordada o no esta vigente
     */
    @Transactional
    public void cancelar(Long reservaId, String dispositivoId) {
        Reserva reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva", reservaId));

        if (!reserva.perteneceA(dispositivoId)) {
            throw new AccessDeniedException("Esta reserva no pertenece a este dispositivo.");
        }
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new ReglaDeNegocioException("Esta reserva ya estaba cancelada.");
        }
        if (reserva.getEstado() == EstadoReserva.ABORDO) {
            throw new ReglaDeNegocioException(
                    "No se puede cancelar una reserva ya marcada como abordada.");
        }

        Instant ahora = Instant.now(reloj);
        if (!reserva.estaVigente(ahora)) {
            throw new ReglaDeNegocioException("Esta reserva ya venció o no está activa.");
        }

        reserva.cancelar(ahora);
    }

    /**
     * Marca EXPIRADA toda reserva vigente cuya vigencia ya vencio. Idempotente:
     * si no hay vencidas no toca nada. Lo llama {@link ExpiradorDeReservas}.
     *
     * @return cuantas reservas se expiraron en esta pasada
     */
    @Transactional
    public int expirarVencidas() {
        return reservas.marcarExpiradas(EstadoReserva.RENOVABLES, Instant.now(reloj));
    }

    private static boolean esViolacionDeReservaVigente(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String mensaje = causa != null ? causa.getMessage() : ex.getMessage();
        return mensaje != null && mensaje.contains(INDICE_VIGENTE);
    }
}
