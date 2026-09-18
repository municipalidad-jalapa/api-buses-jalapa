package gt.muni.jalapa.ecoruta.notificaciones.repositorio;

import gt.muni.jalapa.ecoruta.notificaciones.dominio.DispositivoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoNotificacionRepository
        extends JpaRepository<DispositivoNotificacion, String> {
}
