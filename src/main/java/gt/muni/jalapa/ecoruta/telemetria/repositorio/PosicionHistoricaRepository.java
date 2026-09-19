package gt.muni.jalapa.ecoruta.telemetria.repositorio;

import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PosicionHistoricaRepository extends JpaRepository<PosicionHistorica, Long> {

    /**
     * La posicion vigente de toda la flota.
     *
     * <p>El desempate por id descendente no es adorno: un lote acumulado sin
     * cobertura puede traer dos lecturas con el mismo Instant, y sin el la
     * "posicion actual" seria no determinista.
     */
    Optional<PosicionHistorica> findFirstByOrderByRegistradoEnDescIdDesc();

    Optional<PosicionHistorica> findFirstByVehiculoIdOrderByRegistradoEnDescIdDesc(Long vehiculoId);

    long countByVehiculoId(Long vehiculoId);

    /** Tramo reciente del vehiculo, de la mas nueva a la mas vieja (SCRUM-166). */
    List<PosicionHistorica> findByVehiculoIdAndRegistradoEnGreaterThanEqualOrderByRegistradoEnDescIdDesc(
            Long vehiculoId, Instant desde, Pageable pagina);

    /** SCRUM-24: deduplicacion de reenvios por clave de origen. */
    boolean existsByClaveOrigen(String claveOrigen);
}
