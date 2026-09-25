package gt.muni.jalapa.ecoruta.identidad.config;

import gt.muni.jalapa.ecoruta.identidad.PanelAdminProperties;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primera cuenta de administrador (SCRUM-173, criterio 5).
 *
 * <p>No se siembra por migracion a proposito: Flyway corre en todos los
 * entornos y el uid de produccion no puede quedar en el repositorio. Tampoco hay
 * contrasena que guardar: la identidad vive en Firebase. Basta con definir
 * {@code ECORUTA_ADMIN_FIREBASE_UID} al desplegar; es idempotente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdministradorInicial implements ApplicationRunner {

    private final PanelAdminProperties panel;
    private final UsuarioRepository usuarios;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!panel.hayCuentaInicial()) {
            return;
        }
        if (usuarios.findByFirebaseUid(panel.firebaseUidInicial()).isPresent()) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setUsername(panel.usuarioInicial());
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setFirebaseUid(panel.firebaseUidInicial());
        usuarios.save(admin);
        log.info("Cuenta de administrador inicial creada: usuario={}", panel.usuarioInicial());
    }
}
