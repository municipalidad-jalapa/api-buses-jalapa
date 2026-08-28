package gt.muni.jalapa.ecoruta.seguridad.conductor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link Usuario}.
 *
 * <p>{@code ActivoTrue} va en la consulta a proposito: un usuario desactivado
 * no debe aparecer siquiera. El login (cuando exista en este paquete) no
 * distingue "no existe" de "inactivo"; ambos son credencial rechazada.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);
}
