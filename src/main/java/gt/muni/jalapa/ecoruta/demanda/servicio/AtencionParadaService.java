package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.AccesoDenegadoException;
import gt.muni.jalapa.ecoruta.common.ConflictoException;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.AtencionParadaRepository;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AtenderParadaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AtenderParadaResponse;
import gt.muni.jalapa.ecoruta.seguridad.repositorio.ConductorRutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtencionParadaService {

    /** Dos cierres de la misma parada en menos de esto son un doble toque. */
    static final Duration DOBLE_TOQUE = Duration.ofMinutes(2);

    private final RutaRepository rutas;
    private final ParadaRepository paradas;
    private final ReservaRepository reservas;
    private final AtencionParadaRepository atenciones;
    private final ConductorRutaRepository conductorRuta;
    private final Clock reloj;

    @Transactional
    public AtenderParadaResponse atender(
            Long rutaId,
            Long paradaId,
            String conductor,
            AtenderParadaRequest conteo
    ) {
        rutas.findById(rutaId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Ruta",
                                rutaId
                        ));

        Parada parada = paradas.findById(paradaId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Parada",
                                paradaId
                        ));

        if (!parada.getRuta().getId().equals(rutaId)) {
            throw new RecursoNoEncontradoException(
                    "Parada",
                    paradaId
            );
        }

        if (!conductorRuta.estaAsignadoARuta(
                conductor,
                rutaId
        )) {
            throw new AccesoDenegadoException(
                    "La ruta no pertenece al conductor autenticado."
            );
        }

        Instant ahora = Instant.now(reloj);

        // Varias vueltas por dia: cerrar una parada que ya se cerro en la vuelta
        // en curso empieza la siguiente. Un segundo cierre de la misma parada en
        // pocos minutos es un doble toque, no otra vuelta.
        int vuelta = atenciones.vueltaActual(rutaId);
        var cerradaEnEstaVuelta = atenciones.cerradaEn(rutaId, paradaId, vuelta);
        if (cerradaEnEstaVuelta.isPresent()) {
            if (cerradaEnEstaVuelta.get().isAfter(ahora.minus(DOBLE_TOQUE))) {
                throw new ConflictoException(
                        "La parada ya fue marcada como atendida."
                );
            }
            vuelta++;
        }

        try {
            atenciones.registrar(
                    rutaId,
                    paradaId,
                    conductor,
                    ahora,
                    vuelta,
                    conteo.subieron(),
                    conteo.bajaron()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "La parada ya fue marcada como atendida."
            );
        }

        List<Reserva> pendientes =
                reservas.findByParada_IdAndEstadoIn(
                        paradaId,
                        ReservaService.ESTADOS_VIGENTES
                );

        for (Reserva reserva : pendientes) {
            reserva.marcarAbordo(
                    conductor,
                    ahora
            );
        }

        reservas.saveAll(pendientes);

        return new AtenderParadaResponse(
                pendientes.size(),
                ahora,
                vuelta
        );
    }
}