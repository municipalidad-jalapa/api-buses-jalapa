package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.FuenteAbordaje;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AbordajeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Abordaje del pasajero y correccion del conductor (HU-57). */
@Service
@RequiredArgsConstructor
public class AbordajeService {

    private final ReservaRepository reservas;

    @Transactional
    /**
     * El pasajero responde si logro subir. Solo el dispositivo que creo la
     * reserva puede hacerlo: sin ese control, cualquiera podria cerrar reservas
     * ajenas probando ids, que son secuenciales. Mismo criterio que la
     * cancelacion de HU-124.
     *
     * @throws AccessDeniedException si la reserva es de otro dispositivo (403)
     */
    public AbordajeResponse registrarPasajero(Long reservaId, String dispositivoId, boolean subio) {
        Reserva reserva = cargar(reservaId);
        if (!reserva.perteneceA(dispositivoId)) {
            throw new AccessDeniedException("Esta reserva no pertenece a este dispositivo.");
        }
        if (!reserva.getEstado().permiteAbordajePasajero()) {
            throw new ReglaDeNegocioException("La reserva ya no esta activa");
        }
        aplicar(reserva, subio, FuenteAbordaje.PASAJERO);
        return AbordajeResponse.de(reserva);
    }

    @Transactional
    public AbordajeResponse registrarConductor(Long reservaId, boolean subio) {
        Reserva reserva = cargar(reservaId);
        if (!reserva.getEstado().permiteAbordajeConductor()) {
            throw new ReglaDeNegocioException("La reserva ya no esta activa");
        }
        aplicar(reserva, subio, FuenteAbordaje.CONDUCTOR);
        return AbordajeResponse.de(reserva);
    }

    private Reserva cargar(Long reservaId) {
        return reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva", reservaId));
    }

    private static void aplicar(Reserva reserva, boolean subio, FuenteAbordaje fuente) {
        reserva.setSubio(subio);
        reserva.setAbordajeFuente(fuente);
        reserva.setAbordajeEn(Instant.now());
        reserva.setEstado(subio ? EstadoReserva.ABORDO : EstadoReserva.CANCELADA);
    }
}
