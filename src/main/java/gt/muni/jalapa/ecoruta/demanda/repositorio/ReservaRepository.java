package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Cuenta las reservas del dispositivo
     * que se encuentran en alguno de los
     * estados indicados.
     */
    long countByDispositivoIdAndEstadoIn(
            String dispositivoId,
            Collection<EstadoReserva> estados
    );

    /**
     * HU Desarrollo-95.
     *
     * ¿El dispositivo creó alguna reserva después de {@code desde},
     * sin importar su estado actual?
     *
     * A diferencia de {@link #existeVigentePorDispositivo}, cuenta
     * también las ya canceladas o expiradas: es justo lo que un bucle
     * crear-cancelar necesita para no quedar atrapado nunca por el
     * índice de "una vigente por dispositivo".
     */
    boolean existsByDispositivoIdAndCreadoEnAfter(
            String dispositivoId,
            Instant desde
    );

    /**
     * HU-76.
     *
     * Obtiene las reservas que siguen pendientes
     * en una parada determinada.
     *
     * El servicio envía ACTIVA y RENOVADA,
     * por lo que CANCELADA, EXPIRADA y ABORDO
     * no aparecen.
     */
    List<Reserva> findByParada_IdAndEstadoIn(
            Long paradaId,
            Collection<EstadoReserva> estados
    );

    /**
     * Busca una reserva verificando también
     * el dispositivo que la creó.
     *
     * Utilizado por las operaciones donde el
     * pasajero solamente puede modificar
     * sus propias reservas.
     */
    Optional<Reserva> findByIdAndDispositivoId(
            Long id,
            String dispositivoId
    );

    /**
     * HU-135.
     *
     * Pasa a EXPIRADA toda reserva vigente
     * cuya fecha de expiración ya pasó.
     *
     * Es una actualización masiva para evitar
     * cargar las entidades una por una.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Reserva r
               SET r.estado =
                   gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva.EXPIRADA
             WHERE r.estado IN :estados
               AND r.expiraEn <= :ahora
            """)
    int marcarExpiradas(
            @Param("estados")
            Collection<EstadoReserva> estados,

            @Param("ahora")
            Instant ahora
    );
}