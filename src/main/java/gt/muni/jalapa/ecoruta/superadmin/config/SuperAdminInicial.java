package gt.muni.jalapa.ecoruta.superadmin.config;

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
 * Enlaza la cuenta inicial de SuperAdmin con su identidad de Firebase
 * (SCRUM-26, bloque D, criterio 5).
 *
 * <p>La cuenta la crea la migracion {@code V21}, pero sin uid: Flyway corre en
 * todos los entornos y el uid de produccion no puede quedar en el repositorio.
 * Tampoco hay contrasena que guardar. Con
 * {@code ECORUTA_SUPERADMIN_FIREBASE_UID} definido, el arranque la enlaza; es
 * idempotente y sin la variable no hace nada, asi que un entorno sin configurar
 * simplemente no tiene SuperAdmin que pueda entrar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminInicial implements ApplicationRunner {

    private final PanelAdminProperties panel;
    private final UsuarioRepository usuarios;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!panel.haySuperAdminInicial()) {
            return;
        }
        String uid = panel.superadminFirebaseUid();
        if (usuarios.findByFirebaseUid(uid).isPresent()) {
            return;
        }
        Usuario superadmin = usuarios.findFirstByRol(Rol.SUPERADMIN).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setUsername("superadmin");
            nuevo.setRol(Rol.SUPERADMIN);
            nuevo.setActivo(true);
            return nuevo;
        });
        superadmin.setFirebaseUid(uid);
        superadmin.setActivo(true);
        usuarios.save(superadmin);
        log.info("Cuenta de SuperAdmin enlazada: usuario={}", superadmin.getUsername());
    }
}
