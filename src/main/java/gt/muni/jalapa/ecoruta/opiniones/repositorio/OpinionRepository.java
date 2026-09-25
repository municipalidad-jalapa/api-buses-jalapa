package gt.muni.jalapa.ecoruta.opiniones.repositorio;

import gt.muni.jalapa.ecoruta.opiniones.dominio.Opinion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface OpinionRepository extends JpaRepository<Opinion, Long> {

    /** Limite de envios: va contra idx_opinion_dispositivo. */
    long countByDispositivoIdAndCreadaEnAfter(String dispositivoId, Instant desde);
}
