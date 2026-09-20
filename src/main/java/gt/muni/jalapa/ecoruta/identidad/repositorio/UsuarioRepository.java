package gt.muni.jalapa.ecoruta.identidad.repositorio;

import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByFirebaseUid(String firebaseUid);
}
