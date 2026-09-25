package gt.muni.jalapa.ecoruta.telemetria.repositorio;

import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import org.springframework.data.domain.Pageable;
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

    /**
     * Tramo reciente del vehiculo, de la mas nueva
     * a la mas vieja (SCRUM-166).
     */
    List<PosicionHistorica>
    findByVehiculoIdAndRegistradoEnGreaterThanEqualOrderByRegistradoEnDescIdDesc(
            Long vehiculoId,
            Instant desde,
            Pageable pagina
    );

    /**
     * SCRUM-24: deduplicacion de reenvios
     * por clave de origen.
     */
    boolean existsByClaveOrigen(String claveOrigen);
}
