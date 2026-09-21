package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.pasajeros.PasajeroProperties;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero;
import io.cucumber.java.After;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de {@code roles_y_permisos.feature} (SCRUM-26, bloque D). */
public class PasosDeRolesYPermisos {

    private static final Duration SESION = Duration.ofMinutes(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private PasajeroProperties pasajeroPropiedades;

    private String token;

    @After("@bloque-D")
    public void limpiar() {
        jdbc.update("UPDATE usuarios SET activo = TRUE WHERE rol = 'SUPERADMIN'");
        jdbc.update("DELETE FROM usuarios WHERE username IN ('muni-roles', 'super-roles')");
    }

    @Dado("que tengo una sesión de {string}")
    public void sesion_de(String rol) {
        token = switch (rol) {
            case "superadmin" -> {
                crearCuenta("super-roles", Rol.SUPERADMIN, "uid-super-roles");
                yield emisor.emitir("uid-super-roles", EmisorDeJwt.ROL_SUPERADMIN, SESION).token();
            }
            case "municipalidad" -> {
                crearCuenta("muni-roles", Rol.ADMIN, "uid-muni-roles");
                yield emisor.emitir("uid-muni-roles", EmisorDeJwt.ROL_ADMIN, SESION).token();
            }
            case "piloto" -> emisor.emitirParaConductor("conductor1").token();
            case "pasajero" -> emisor.emitir("1", SesionDePasajero.ROL,
                    pasajeroPropiedades.sesion()).token();
            default -> throw new IllegalArgumentException("Rol desconocido: " + rol);
        };
    }

    @Cuando("administro las cuentas")
    public void administro_cuentas() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/superadmin/cuentas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Cuando("administro los vehículos")
    public void administro_vehiculos() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/admin/vehiculos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Cuando("consulto el panel municipal como ese rol")
    public void consulto_el_panel() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/admin/servicio")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Cuando("marco como atendida una parada de otra ruta")
    public void marco_parada_ajena() throws Exception {
        // conductor1 tiene asignada la ruta 1; la parada es de la segunda ruta.
        Long paradaAjena = jdbc.queryForObject(
                "SELECT id FROM paradas WHERE ruta_id <> 1 ORDER BY id LIMIT 1", Long.class);
        Long rutaAjena = jdbc.queryForObject(
                "SELECT ruta_id FROM paradas WHERE id = ?", Long.class, paradaAjena);
        contexto.guardarRespuesta(mockMvc.perform(
                post("/api/v1/rutas/{ruta}/paradas/{parada}/atendida", rutaAjena, paradaAjena)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Y("que solo queda un SuperAdmin activo")
    public void solo_queda_uno() {
        jdbc.update("""
                UPDATE usuarios SET activo = FALSE
                 WHERE rol = 'SUPERADMIN' AND firebase_uid IS DISTINCT FROM 'uid-super-roles'
                """);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE rol = 'SUPERADMIN' AND activo", Integer.class))
                .isEqualTo(1);
    }

    @Cuando("desactivo ese SuperAdmin")
    public void desactivo_ese_superadmin() throws Exception {
        Long id = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE rol = 'SUPERADMIN' AND activo", Long.class);
        contexto.guardarRespuesta(mockMvc.perform(
                post("/api/v1/superadmin/cuentas/{id}/desactivacion", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Entonces("los roles de operación son {string}, {string} y {string}")
    public void los_roles_de_operacion(String uno, String dos, String tres) {
        assertThat(Arrays.stream(Rol.values()).map(Enum::name))
                .containsExactlyInAnyOrder(uno, dos, tres);
    }

    @Y("el pasajero tiene su propio rol fuera de las cuentas de operación")
    public void el_pasajero_tiene_su_rol() {
        assertThat(SesionDePasajero.ROL).isEqualTo("pasajero");
        assertThat(Arrays.stream(Rol.values()).map(Enum::name))
                .doesNotContain("PASAJERO");
    }

    @Y("existe la cuenta inicial de SuperAdmin sin contraseña guardada")
    public void existe_la_cuenta_inicial() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM usuarios
                 WHERE rol = 'SUPERADMIN' AND username = 'superadmin' AND password_hash IS NULL
                """, Integer.class)).isEqualTo(1);
    }

    private void crearCuenta(String username, Rol rol, String uid) {
        jdbc.update("DELETE FROM usuarios WHERE username = ?", username);
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid) VALUES (?, ?, TRUE, ?)
                """, username, rol.name(), uid);
    }
}
