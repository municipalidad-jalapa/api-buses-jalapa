package gt.muni.jalapa.ecoruta.precision.repositorio;

import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PrediccionEtaRepository extends JpaRepository<PrediccionEta, Long> {

    /**
     * Candidatas vigentes: de esa ruta/parada/vehiculo, sin llegada, emitidas
     * antes (o al mismo tiempo) del arribo. La primera es la vigente.
     */
    @Query("""
            SELECT p FROM PrediccionEta p
             WHERE p.rutaId = :rutaId
               AND p.paradaId = :paradaId
               AND p.vehiculoId = :vehiculoId
               AND p.predichoEn <= :llegadaEn
               AND NOT EXISTS (SELECT 1 FROM LlegadaReal l WHERE l.prediccion = p)
             ORDER BY p.predichoEn DESC, p.id DESC
            """)
    List<PrediccionEta> candidatasVigentes(Long rutaId, Long paradaId, Long vehiculoId, Instant llegadaEn);

    default Optional<PrediccionEta> vigente(Long rutaId, Long paradaId, Long vehiculoId, Instant llegadaEn) {
        return candidatasVigentes(rutaId, paradaId, vehiculoId, llegadaEn).stream().findFirst();
    }
}
