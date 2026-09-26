package gt.muni.jalapa.ecoruta.precision.repositorio;

import gt.muni.jalapa.ecoruta.precision.dominio.LlegadaReal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface LlegadaRealRepository extends JpaRepository<LlegadaReal, Long> {

    @Query("""
            SELECT l FROM LlegadaReal l
            JOIN FETCH l.prediccion p
             WHERE p.rutaId = :rutaId
               AND l.llegadaEn >= :desde
               AND l.llegadaEn <= :hasta
            """)
    List<LlegadaReal> deLaRutaEntre(Long rutaId, Instant desde, Instant hasta);
}
