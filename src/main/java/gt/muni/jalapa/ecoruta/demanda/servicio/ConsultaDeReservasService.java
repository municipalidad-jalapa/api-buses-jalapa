package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservasDeParadaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lectura de la demanda vigente de una parada.
 *
 * <p>Parada inexistente es 404, no lista vacia: cero reservas en una parada
 * real es un dato (nadie espera); una parada que no existe es un id
 * inventado y no se puede confundir con "no hay demanda".
 */
@Service
@RequiredArgsConstructor
public class ConsultaDeReservasService {

    private final ParadaRepository paradaRepository;
    private final ReservaRepository reservaRepository;

    @Transactional(readOnly = true)
    public ReservasDeParadaResponse consultar(Long paradaId) {
        if (!paradaRepository.existsById(paradaId)) {
            throw new RecursoNoEncontradoException("Parada", paradaId);
        }
        List<Reserva> reservas = reservaRepository.findByParadaIdAndEstadoInOrderByExpiraEnAsc(
                paradaId, EstadoReserva.vigentes());
        return ReservasDeParadaResponse.de(paradaId, reservas);
    }
}
