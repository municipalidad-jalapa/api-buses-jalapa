package gt.muni.jalapa.ecoruta.pasajeros.repositorio;

import gt.muni.jalapa.ecoruta.pasajeros.dominio.Pasajero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasajeroRepository extends JpaRepository<Pasajero, Long> {

    Optional<Pasajero> findByFirebaseUid(String firebaseUid);
}
