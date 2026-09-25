package gt.muni.jalapa.ecoruta.integraciones.traccar.repositorio;

import gt.muni.jalapa.ecoruta.integraciones.traccar.dominio.DispositivoExterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DispositivoExternoRepository extends JpaRepository<DispositivoExterno, Long> {

    @Query("""
            SELECT d FROM DispositivoExterno d
            JOIN FETCH d.equipo e
            LEFT JOIN FETCH e.vehiculo
            WHERE d.proveedor = :proveedor AND d.identificador = :identificador
            """)
    Optional<DispositivoExterno> buscar(String proveedor, String identificador);

    List<DispositivoExterno> findByProveedorAndEquipoId(String proveedor, Long equipoId);

    /**
     * El GPS que tiene cada bus hoy: solo cuenta el que apunta al equipo ACTIVO,
     * porque es el unico cuyas posiciones acepta la recepcion de Traccar.
     */
    @Query("""
            SELECT d FROM DispositivoExterno d
            JOIN FETCH d.equipo e
            JOIN FETCH e.vehiculo
            WHERE d.proveedor = :proveedor
              AND e.estado = gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo.ACTIVO
            """)
    List<DispositivoExterno> vigentes(String proveedor);
}
