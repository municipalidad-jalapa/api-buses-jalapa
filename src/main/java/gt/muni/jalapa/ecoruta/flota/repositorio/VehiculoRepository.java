package gt.muni.jalapa.ecoruta.flota.repositorio;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByIdentificador(String identificador);

    boolean existsByPlaca(String placa);

    List<Vehiculo> findAllByOrderByIdentificadorAsc();
}
