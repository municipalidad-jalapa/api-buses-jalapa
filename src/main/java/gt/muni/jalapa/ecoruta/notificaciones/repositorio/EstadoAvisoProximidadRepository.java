package gt.muni.jalapa.ecoruta.notificaciones.repositorio;

import gt.muni.jalapa.ecoruta.notificaciones.dominio.EstadoAvisoProximidad;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoAvisoProximidadRepository
        extends JpaRepository<EstadoAvisoProximidad, Long> {

    Optional<EstadoAvisoProximidad> findByReservaIdAndTipo(Long reservaId, TipoAviso tipo);
}
