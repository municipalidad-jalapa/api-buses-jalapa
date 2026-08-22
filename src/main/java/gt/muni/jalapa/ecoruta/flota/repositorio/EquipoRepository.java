package gt.muni.jalapa.ecoruta.flota.repositorio;

import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    /** Se ejecuta en cada peticion de ingesta; va contra uq_equipo_codigo_publico. */
    Optional<Equipo> findByCodigoPublico(String codigoPublico);

    Optional<Equipo> findByVehiculoIdAndEstado(Long vehiculoId, EstadoEquipo estado);

    /**
     * Todos los equipos que ha llevado un vehiculo, del mas nuevo al mas viejo.
     * Como rotar es revocar y crear, esta consulta es el historial de asignaciones
     * sin necesidad de una segunda tabla.
     */
    List<Equipo> findByVehiculoIdOrderByCreadoEnDesc(Long vehiculoId);

    /**
     * El vehiculo va con join fetch a proposito: la lista se serializa en la capa
     * web, donde ya no hay sesion (open-in-view esta en false) y navegar el proxy
     * LAZY lanzaria LazyInitializationException.
     */
    @Query("SELECT e FROM Equipo e LEFT JOIN FETCH e.vehiculo ORDER BY e.creadoEn DESC")
    List<Equipo> listarConVehiculo();
}
