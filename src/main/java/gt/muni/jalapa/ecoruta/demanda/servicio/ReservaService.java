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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Servicio de reservas de espera en parada.
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    /**
     * Solo estos estados bloquean una reserva nueva.
     * ABORDO no es vigente.
     */
    static final Set<EstadoReserva> ESTADOS_VIGENTES =
            EnumSet.of(
                    EstadoReserva.ACTIVA,
                    EstadoReserva.RENOVADA
            );

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
                    "Debes acercarte mas a la parada para registrar que estas esperando."
            );
        }

        if (reservas.existeVigentePorDispositivo(
                peticion.dispositivoId(),
                ESTADOS_VIGENTES
        )) {
            throw new ReglaDeNegocioException(
                    "Este dispositivo ya tiene una reserva activa."
            );
        }

        Instant ahora = Instant.now(reloj);

        Reserva reserva = new Reserva(
                peticion.dispositivoId(),
                parada,
                EstadoReserva.ACTIVA,
                ahora,
                ahora.plus(demanda.ttl())
        );

        try {

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
     * HU-76.
     *
     * Permite que el pasajero consulte su reserva
     * utilizando el id de la reserva y el id de
     * su dispositivo.
     *
     * De esta forma puede ver cuando el conductor
     * ya marco su reserva como ABORDO.
     */
    @Transactional(readOnly = true)
    public DetalleReservaResponse consultar(
            Long reservaId,
            String dispositivoId
    ) {

        Reserva reserva =
                buscarReservaDelDispositivo(
                        reservaId,
                        dispositivoId
                );

        return DetalleReservaResponse.de(reserva);
    }

    /**
     * HU-76.
     *
     * El pasajero indica que considera que no abordo.
     *
     * Esta declaracion se guarda por separado y
     * no reemplaza el estado oficial de la reserva.
     *
     * Si posteriormente el conductor marca el
     * abordaje, el estado pasa a ABORDO pero esta
     * declaracion permanece guardada.
     */
    @Transactional
    public DetalleReservaResponse declararNoAbordo(
            Long reservaId,
            String dispositivoId
    ) {

        Reserva reserva =
                buscarReservaDelDispositivo(
                        reservaId,
                        dispositivoId
                );

        /*
         * Evitamos reemplazar la fecha original si
         * el pasajero envia la misma declaracion
         * varias veces.
         */
        if (!reserva.isPasajeroDeclaroNoAbordo()) {

            Instant ahora = Instant.now(reloj);

            reserva.declararNoAbordo(ahora);
        }

        return DetalleReservaResponse.de(reserva);
    }

    /**
     * Busca una reserva asegurando que pertenece
     * al dispositivo indicado.
     *
     * Si el id existe pero pertenece a otro
     * dispositivo, tambien devolvemos 404.
     */
    private Reserva buscarReservaDelDispositivo(
            Long reservaId,
            String dispositivoId
    ) {

        return reservas
                .findByIdAndDispositivoId(
                        reservaId,
                        dispositivoId
                )
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Reserva",
                                reservaId
                        )
                );
    }

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