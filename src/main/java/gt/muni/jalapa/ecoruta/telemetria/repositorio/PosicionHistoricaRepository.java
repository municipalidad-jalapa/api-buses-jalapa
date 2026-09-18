package gt.muni.jalapa.ecoruta.telemetria.repositorio;

import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PosicionHistoricaRepository
        extends JpaRepository<PosicionHistorica, Long> {

    Optional<PosicionHistorica>
    findFirstByOrderByRegistradoEnDescIdDesc();

    Optional<PosicionHistorica>
    findFirstByVehiculoIdOrderByRegistradoEnDescIdDesc(
            Long vehiculoId
    );

    long countByVehiculoId(Long vehiculoId);

    /**
     * HU-85.
     *
     * Recupera el recorrido de un vehiculo dentro
     * de un rango de tiempo y lo devuelve
     * cronologicamente.
     */
    List<PosicionHistorica>
    findByVehiculoIdAndRegistradoEnGreaterThanEqualAndRegistradoEnLessThanOrderByRegistradoEnAscIdAsc(
            Long vehiculoId,
            Instant desde,
            Instant hasta
    );
}
