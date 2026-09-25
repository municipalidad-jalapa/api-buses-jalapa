package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.DetalleReservaResponse;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Ciclo de vida de la reserva de espera en parada.
 *
 * SCRUM-306 crea la reserva.
 * HU-135 permite renovarla y expirar las vencidas.
 * HU-124 permite cancelarla.
 * HU-76 permite consultar su estado y registrar
 * la declaración del pasajero cuando considera
 * que no logró abordar.
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    /**
     * Solo ACTIVA y RENOVADA cuentan
     * como reservas vigentes.
     *
     * ABORDO no ocupa el cupo.
     */
    static final Set<EstadoReserva> ESTADOS_VIGENTES =
            EstadoReserva.RENOVABLES;

    private static final String INDICE_VIGENTE =
            "uq_registro_activo_por_dispositivo";

    private final ReservaRepository reservas;
    private final ParadaRepository paradas;
    private final DemandaProperties demanda;
    private final Clock reloj;

    /**
     * Crea una nueva reserva.
     */
    @Transactional
    public ReservaResponse crear(
            CrearReservaRequest peticion
    ) {

        Parada parada = paradas
                .findById(peticion.paradaId())
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Parada",
                                peticion.paradaId()
                        )
                );

        if (!paradas.estaDentroDeGeocerca(
                parada.getId(),
                peticion.latitud(),
                peticion.longitud(),
                demanda.geocercaMetros()
        )) {
            throw new ReglaDeNegocioException(
                    "Debes acercarte más a la parada para registrar que estás esperando."
            );
        }

        if (reservas.existsByDispositivoIdAndEstadoIn(
                peticion.dispositivoId(),
                ESTADOS_VIGENTES
        )) {
            throw new ReglaDeNegocioException(
                    "Este dispositivo ya tiene una reserva activa."
            );
        }

        Instant ahora = Instant.now(reloj);

        /*
         * HU Desarrollo-95.
         *
         * El chequeo de arriba solo bloquea mientras haya una reserva
         * vigente. Sin este, cancelar y volver a crear de inmediato deja
         * crecer el conteo historico de demanda a un ritmo que ninguna
         * persona real puede sostener.
         */
        if (reservas.existsByDispositivoIdAndCreadoEnAfter(
                peticion.dispositivoId(),
                ahora.minus(demanda.ritmoMinimo())
        )) {
            throw new ReglaDeNegocioException(
                    "Este dispositivo está registrando demanda a un ritmo que no es posible "
                            + "para una persona. Espera unos segundos e intenta de nuevo."
            );
        }

        Reserva reserva = new Reserva(
                peticion.dispositivoId(),
                parada,
                EstadoReserva.ACTIVA,
                ahora,
                ahora.plus(demanda.vigencia())
        );

        try {
            /*
             * saveAndFlush fuerza el INSERT inmediatamente.
             * El índice parcial es la última protección
             * frente a solicitudes concurrentes.
             */
            Reserva guardada =
                    reservas.saveAndFlush(reserva);

            return ReservaResponse.de(guardada);

        } catch (DataIntegrityViolationException ex) {

            if (esViolacionDeReservaVigente(ex)) {
                throw new ReglaDeNegocioException(
                        "Este dispositivo ya tiene una reserva activa."
                );
            }

            throw ex;
        }
    }

    /**
     * HU-135.
     *
     * Extiende la vigencia de una reserva.
     * Conserva el mismo identificador
     * y pasa a estado RENOVADA.
     */
    @Transactional
    public ReservaResponse renovar(
            Long reservaId,
            String dispositivoId
    ) {
        return renovar(reservaId, dispositivoId, null);
    }

    /**
     * HU-135 con la cuenta del bloque B: renueva quien creo la reserva desde
     * este navegador o el pasajero autenticado al que pertenece.
     */
    @Transactional
    public ReservaResponse renovar(
            Long reservaId,
            String dispositivoId,
            Long pasajeroId
    ) {

        Reserva reserva = reservas
                .findById(reservaId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Reserva",
                                reservaId
                        )
                );

        if (!reserva.perteneceA(dispositivoId, pasajeroId)) {
            throw new AccessDeniedException(
                    "Esta reserva no pertenece a este dispositivo."
            );
        }

        Instant ahora = Instant.now(reloj);

        if (!reserva.estaVigente(ahora)) {
            throw new ReglaDeNegocioException(
                    "Esta reserva ya venció o no está activa."
            );
        }

        reserva.renovar(
                ahora.plus(demanda.renovacion())
        );

        return ReservaResponse.de(reserva);
    }

    /**
     * HU-124.
     *
     * El pasajero cancela manualmente su reserva.
     *
     * La reserva no se elimina de la base de datos:
     * cambia a CANCELADA y conserva cuándo ocurrió.
     */
    @Transactional
    public void cancelar(
            Long reservaId,
            String dispositivoId
    ) {
        cancelar(reservaId, dispositivoId, null);
    }

    /**
     * HU-124 con la cuenta del bloque B: cancela quien creo la reserva desde
     * este navegador o el pasajero autenticado al que pertenece.
     */
    @Transactional
    public void cancelar(
            Long reservaId,
            String dispositivoId,
            Long pasajeroId
    ) {

        Reserva reserva = reservas
                .findById(reservaId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Reserva",
                                reservaId
                        )
                );

        if (!reserva.perteneceA(dispositivoId, pasajeroId)) {
            throw new AccessDeniedException(
                    "Esta reserva no pertenece a este dispositivo."
            );
        }

        if (reserva.getEstado()
                == EstadoReserva.CANCELADA) {

            throw new ReglaDeNegocioException(
                    "Esta reserva ya estaba cancelada."
            );
        }

        Instant ahora = Instant.now(reloj);

        if (!reserva.estaVigente(ahora)) {
            throw new ReglaDeNegocioException(
                    "Esta reserva ya venció o no está activa."
            );
        }

        reserva.cancelar(ahora);
    }

    /**
     * HU-135.
     *
     * Marca como EXPIRADA toda reserva
     * ACTIVA o RENOVADA cuya vigencia ya terminó.
     *
     * Este método es idempotente.
     */
    @Transactional
    public int expirarVencidas() {

        return reservas.marcarExpiradas(
                EstadoReserva.RENOVABLES,
                Instant.now(reloj)
        );
    }

    /**
     * HU-76.
     *
     * Permite al pasajero consultar su reserva
     * utilizando el identificador de la reserva
     * y su identificador de dispositivo.
     *
     * De esta forma puede detectar cuando
     * el conductor ya la marcó como ABORDO.
     */
    @Transactional(readOnly = true)
    public DetalleReservaResponse consultar(
            Long reservaId,
            String dispositivoId
    ) {
        return consultar(reservaId, dispositivoId, null);
    }

    /**
     * HU-76 con la cuenta del bloque B.
     */
    @Transactional(readOnly = true)
    public DetalleReservaResponse consultar(
            Long reservaId,
            String dispositivoId,
            Long pasajeroId
    ) {

        Reserva reserva =
                buscarReservaDelPasajero(
                        reservaId,
                        dispositivoId,
                        pasajeroId
                );

        return DetalleReservaResponse.de(reserva);
    }

    /**
     * SCRUM-26, bloque B.2, criterio 4.
     *
     * Reservas de la cuenta autenticada, de la mas reciente a la mas antigua.
     * Solo las suyas: lo anonimo de otro navegador no aparece aqui.
     */
    @Transactional(readOnly = true)
    public List<DetalleReservaResponse> misReservas(Long pasajeroId) {

        return reservas
                .findByPasajeroIdOrderByCreadoEnDesc(pasajeroId)
                .stream()
                .map(DetalleReservaResponse::de)
                .toList();
    }

    /**
     * HU-76.
     *
     * El pasajero indica que considera
     * que NO logró abordar.
     *
     * La declaración queda guardada de forma
     * independiente para que no se pierda si
     * posteriormente el conductor confirma
     * que sí abordó.
     */
    @Transactional
    public DetalleReservaResponse declararNoAbordo(
            Long reservaId,
            String dispositivoId
    ) {
        return declararNoAbordo(reservaId, dispositivoId, null);
    }

    /**
     * HU-76 con la cuenta del bloque B.
     */
    @Transactional
    public DetalleReservaResponse declararNoAbordo(
            Long reservaId,
            String dispositivoId,
            Long pasajeroId
    ) {

        Reserva reserva =
                buscarReservaDelPasajero(
                        reservaId,
                        dispositivoId,
                        pasajeroId
                );

        /*
         * Si ya hizo la declaración anteriormente,
         * conservamos la fecha original.
         */
        if (!reserva.isPasajeroDeclaroNoAbordo()) {

            Instant ahora =
                    Instant.now(reloj);

            reserva.declararNoAbordo(ahora);
        }

        return DetalleReservaResponse.de(reserva);
    }

    /**
     * HU-76.
     *
     * Busca una reserva asegurando que
     * pertenece al dispositivo solicitado.
     *
     * Para esta operación devolvemos 404
     * tanto si no existe como si pertenece
     * a otro dispositivo.
     */
    private Reserva buscarReservaDelPasajero(
            Long reservaId,
            String dispositivoId,
            Long pasajeroId
    ) {

        return reservas
                .findById(reservaId)
                .filter(reserva ->
                        reserva.perteneceA(
                                dispositivoId,
                                pasajeroId
                        )
                )
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Reserva",
                                reservaId
                        )
                );
    }

    /**
     * Detecta la violación del índice que impide
     * tener más de una reserva vigente
     * por dispositivo.
     */
    private static boolean esViolacionDeReservaVigente(
            DataIntegrityViolationException ex
    ) {

        Throwable causa =
                ex.getMostSpecificCause();

        String mensaje =
                causa != null
                        ? causa.getMessage()
                        : ex.getMessage();

        return mensaje != null
                && mensaje.contains(INDICE_VIGENTE);
    }
}