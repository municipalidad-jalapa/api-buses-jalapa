package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Reservas de una parada en los estados pedidos, las que vencen primero
     * primero. El orden por {@code expiraEn} ascendente es el de la cola:
     * quien tiene el TTL mas cercano sale primero. No se usa fecha de
     * insercion porque {@code creado_en} no se mapea en este modulo.
     */
    List<Reserva> findByParadaIdAndEstadoInOrderByExpiraEnAsc(
            Long paradaId, Collection<EstadoReserva> estados);
}
