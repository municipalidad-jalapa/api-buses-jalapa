package gt.muni.jalapa.ecoruta.atrasos.repositorio;

import gt.muni.jalapa.ecoruta.atrasos.dominio.AvisoDeAtraso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AvisoDeAtrasoRepository extends JpaRepository<AvisoDeAtraso, Long> {

    /** El aviso vigente de la ruta: el ultimo que no se cancelo ni vencio. */
    Optional<AvisoDeAtraso> findFirstByRutaIdAndCanceladoEnIsNullAndVigenteHastaAfterOrderByReportadoEnDesc(
            Long rutaId, Instant ahora);

    /** Los que siguen abiertos en la ruta: al reportar uno nuevo se cierran. */
    List<AvisoDeAtraso> findByRutaIdAndCanceladoEnIsNull(Long rutaId);
}
