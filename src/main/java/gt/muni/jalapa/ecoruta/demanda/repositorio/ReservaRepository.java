package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Hay ya una reserva del dispositivo en alguno de los estados que ocupan el
     * cupo. Se consulta antes de crear para responder un 422 legible en vez de
     * dejar que reviente {@code uq_reserva_vigente_por_dispositivo}.
     */
    boolean existsByDispositivoIdAndEstadoIn(String dispositivoId, Collection<EstadoReserva> estados);

    /**
     * Pasa a EXPIRADA toda reserva vigente cuya fecha de expiracion ya paso. Es
     * un UPDATE masivo: lo corre la tarea programada sin cargar entidades.
     *
     * <p>{@code clearAutomatically} vacia el contexto de persistencia despues,
     * para que nadie siga viendo el estado viejo de una fila recien tocada.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Reserva r
               SET r.estado = gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva.EXPIRADA
             WHERE r.estado IN :estados
               AND r.expiraEn <= :ahora
            """)
    int marcarExpiradas(@Param("estados") Collection<EstadoReserva> estados,
                        @Param("ahora") Instant ahora);
}
