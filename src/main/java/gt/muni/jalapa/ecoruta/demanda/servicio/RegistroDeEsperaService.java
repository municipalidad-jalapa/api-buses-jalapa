package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.DemandaProperties;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Alta minima de un registro de espera. HU-57 necesita reservas activas para
 * avisar; la geocerca de registro es otra historia.
 */
@Service
@RequiredArgsConstructor
public class RegistroDeEsperaService {

    private final ReservaRepository reservas;
    private final DemandaProperties propiedades;
    private final JdbcTemplate jdbc;

    @Transactional
    public Reserva registrar(String dispositivoId, Long paradaId) {
        if (!paradaExiste(paradaId)) {
            throw new RecursoNoEncontradoException("Parada", paradaId);
        }
        Reserva reserva = new Reserva();
        reserva.setDispositivoId(dispositivoId);
        reserva.setParadaId(paradaId);
        reserva.setEstado(EstadoReserva.ACTIVA);
        reserva.setCreadoEn(Instant.now());
        reserva.setExpiraEn(Instant.now().plus(propiedades.ttl()));
        try {
            return reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaDeNegocioException(
                    "El dispositivo ya tiene una reserva activa");
        }
    }

    private boolean paradaExiste(Long paradaId) {
        Integer cuantas = jdbc.queryForObject(
                "SELECT count(*) FROM paradas WHERE id = ?", Integer.class, paradaId);
        return cuantas != null && cuantas > 0;
    }
}
