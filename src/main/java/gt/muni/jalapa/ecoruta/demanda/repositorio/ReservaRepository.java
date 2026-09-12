package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository
        extends JpaRepository<Reserva, Long> {

    /**
     * Una sola consulta:
     * ¿el dispositivo ya tiene una reserva vigente?
     *
     * Vigente = ACTIVA o RENOVADA.
     * ABORDO no cuenta.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0
                   THEN TRUE ELSE FALSE END
              FROM Reserva r
             WHERE r.dispositivoId = :dispositivoId
               AND r.estado IN :estados
            """)
    boolean existeVigentePorDispositivo(
            @Param("dispositivoId")
            String dispositivoId,

            @Param("estados")
            Collection<EstadoReserva> estados
    );

    long countByDispositivoIdAndEstadoIn(
            String dispositivoId,
            Collection<EstadoReserva> estados
    );

    /**
     * HU-76.
     *
     * Obtiene solamente las reservas que siguen
     * esperando en una parada.
     *
     * CANCELADA, EXPIRADA y ABORDO nunca aparecen.
     */
    List<Reserva> findByParada_IdAndEstadoIn(
            Long paradaId,
            Collection<EstadoReserva> estados
    );

    Optional<Reserva> findByIdAndDispositivoId(
        Long id,
        String dispositivoId
  );

}
