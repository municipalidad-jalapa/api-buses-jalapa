package gt.muni.jalapa.ecoruta.identidad.config;

import gt.muni.jalapa.ecoruta.identidad.PanelAdminProperties;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-173, criterio 5: la primera cuenta de administrador, sin contrasena en el repo. */
class AdministradorInicialTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    @Test
    void crea_la_cuenta_admin_con_el_uid_configurado_y_sin_contrasena() {
        when(usuarios.findByFirebaseUid("uid-muni")).thenReturn(Optional.empty());

        new AdministradorInicial(new PanelAdminProperties(30, 120, "uid-muni", "jefe-transporte"), usuarios)
                .run(null);

        ArgumentCaptor<Usuario> creado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(creado.capture());
        assertThat(creado.getValue().getRol()).isEqualTo(Rol.ADMIN);
        assertThat(creado.getValue().getFirebaseUid()).isEqualTo("uid-muni");
        assertThat(creado.getValue().getUsername()).isEqualTo("jefe-transporte");
        assertThat(creado.getValue().getPasswordHash()).isNull();
    }

    @Test
    void es_idempotente_si_la_cuenta_ya_existe() {
        when(usuarios.findByFirebaseUid("uid-muni")).thenReturn(Optional.of(new Usuario()));

        new AdministradorInicial(new PanelAdminProperties(30, 120, "uid-muni", null), usuarios).run(null);

        verify(usuarios, never()).save(any());
    }

    @Test
    void sin_uid_configurado_no_crea_nada() {
        new AdministradorInicial(new PanelAdminProperties(0, 0, "", null), usuarios).run(null);

        verify(usuarios, never()).save(any());
    }
}
