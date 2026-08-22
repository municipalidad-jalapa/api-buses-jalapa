package gt.muni.jalapa.ecoruta.telemetria.repositorio;

import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
