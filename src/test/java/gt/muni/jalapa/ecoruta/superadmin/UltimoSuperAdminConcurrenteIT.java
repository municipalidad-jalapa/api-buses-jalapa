package gt.muni.jalapa.ecoruta.superadmin;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.superadmin.servicio.AdministracionDelSistema;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.EditarCuentaRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Dos transacciones reales compiten por retirar los dos últimos administradores. */
class UltimoSuperAdminConcurrenteIT extends IntegracionPostgisTest {
    @Autowired AdministracionDelSistema administracion;
    @Autowired DataSource datos;

    @ParameterizedTest
    @ValueSource(strings = {"desactivar", "rol", "mixto"})
    void nunca_quedan_cero_superadmin(String modo) throws Exception {
        var originales = jdbc.queryForList(
                "SELECT id FROM usuarios WHERE rol = 'SUPERADMIN' AND activo", Long.class);
        long uno = crear("concurrencia-uno");
        long dos = crear("concurrencia-dos");
        var ejecutor = Executors.newFixedThreadPool(2);
        try (Connection bloqueo = datos.getConnection()) {
            jdbc.update("UPDATE usuarios SET activo = false WHERE rol = 'SUPERADMIN' AND id NOT IN (?, ?)", uno, dos);
            bloqueo.setAutoCommit(false);
            try (var sentencia = bloqueo.prepareStatement("SELECT id FROM usuarios WHERE id IN (?, ?) FOR UPDATE")) {
                sentencia.setLong(1, uno);
                sentencia.setLong(2, dos);
                sentencia.executeQuery().close();
            }
            try {
                var primera = ejecutor.submit(retirar(uno, modo.equals("rol")));
                var segunda = ejecutor.submit(retirar(dos, !modo.equals("desactivar")));
                // Antes del arreglo ambas escrituras esperan las filas tras contar dos activos.
                // Con el arreglo una espera la fila y la otra el bloqueo de la invariancia.
                long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                int esperando;
                do {
                    esperando = jdbc.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND pid <> pg_backend_pid()
                              AND wait_event_type = 'Lock'
                            """, Integer.class);
                    if (esperando < 2) Thread.sleep(25);
                } while (esperando < 2 && System.nanoTime() < limite);
                assertThat(esperando).as("dos operaciones realmente concurrentes bloqueadas en PostgreSQL").isGreaterThanOrEqualTo(2);
                bloqueo.commit();
                assertThat(java.util.List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE rol = 'SUPERADMIN' AND activo", Integer.class))
                        .isEqualTo(1);
            } finally {
                bloqueo.rollback();
            }
        } finally {
            ejecutor.shutdown();
            assertThat(ejecutor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            for (Long id : originales) jdbc.update("UPDATE usuarios SET activo = true WHERE id = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", uno, dos);
        }
    }

    private long crear(String nombre) {
        return jdbc.queryForObject("INSERT INTO usuarios(username, rol, activo) VALUES (?, 'SUPERADMIN', true) RETURNING id",
                Long.class, nombre);
    }

    private Callable<Boolean> retirar(long id, boolean cambiarRol) {
        return () -> {
            try {
                if (cambiarRol) administracion.editarCuenta(id, new EditarCuentaRequest(Rol.ADMIN, null, null, null));
                else administracion.desactivarCuenta(id);
                return true;
            } catch (ReglaDeNegocioException esperada) {
                assertThat(esperada.getMessage()).contains("ultimo SuperAdmin");
                return false;
            }
        };
    }
}
