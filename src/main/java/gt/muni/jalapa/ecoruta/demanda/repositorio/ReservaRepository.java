package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
}
