package gt.muni.jalapa.ecoruta.integraciones.traccar.repositorio;

import gt.muni.jalapa.ecoruta.integraciones.traccar.dominio.DispositivoExterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DispositivoExternoRepository extends JpaRepository<DispositivoExterno, Long> {

    /** Con el equipo y su vehiculo ya cargados: quien llama los navega enseguida. */
    @Query("""
            SELECT d FROM DispositivoExterno d
            JOIN FETCH d.equipo e
            LEFT JOIN FETCH e.vehiculo
            WHERE d.proveedor = :proveedor AND d.identificador = :identificador
            """)
    Optional<DispositivoExterno> buscar(String proveedor, String identificador);
}
