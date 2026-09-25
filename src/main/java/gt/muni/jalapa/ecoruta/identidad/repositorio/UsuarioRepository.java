package gt.muni.jalapa.ecoruta.identidad.repositorio;

import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByFirebaseUid(String firebaseUid);

    Optional<Usuario> findByUsername(String username);

    /** SCRUM-26, bloque D: cuentas de operacion para el SuperAdmin. */
    List<Usuario> findAllByOrderByUsernameAsc();

    Optional<Usuario> findFirstByRol(Rol rol);

    boolean existsByUsername(String username);
}
