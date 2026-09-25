package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.identidad.PanelAdminProperties;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.identidad.servicio.AutenticacionDeAdministrador;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.identidad.web.dto.SesionAdminResponse;
import io.cucumber.java.After;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Pasos de {@code entrar_al_panel_municipal.feature} (SCRUM-173).
 *
 * <p>Firebase no se sustituye con {@code @MockitoBean}: eso levantaria otro
 * contexto de Spring. El servicio de login se arma con un verificador de prueba
 * que da por buena la identidad; el resto del mecanismo es el real.
 */
public class PasosDelPanelMunicipal {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private PanelAdminProperties panel;

    private SesionAdminResponse sesion;
    private String uidIntentado;

    @After("@SCRUM-173")
    public void borrarCuentas() {
        jdbc.update("DELETE FROM usuarios WHERE username LIKE '%-bdd'");
    }

    @Dado("que existe una cuenta de administrador con uid {string}")
    public void cuenta_admin(String uid) {
        crearCuenta("admin-bdd", "ADMIN", uid);
    }

    @Dado("que existe una cuenta de conductor con uid {string}")
    public void cuenta_conductor(String uid) {
        crearCuenta("conductor-bdd", "CONDUCTOR", uid);
    }

    @Dado("que el plazo de inactividad configurado es de {int} minutos")
    public void plazo_configurado(int minutos) {
        assertThat(panel.inactividadMinutos()).isEqualTo(minutos);
    }

    @Cuando("la cuenta con uid {string} inicia sesión en el panel")
    public void inicia_sesion(String uid) {
        sesion = login().iniciarSesion(uid);
    }

    @Cuando("la cuenta con uid {string} intenta iniciar sesión en el panel")
    public void intenta_iniciar_sesion(String uid) {
        uidIntentado = uid;
    }

    @Entonces("recibe un JWT del backend con rol {string}")
    public void recibe_jwt(String rol) {
        assertThat(sesion.rol()).isEqualTo(rol);
        assertThat(emisor.leer(sesion.token(), rol)).isPresent();
    }

    @Entonces("la sesión vence en {int} minutos si no se renueva")
    public void vence_en(int minutos) {
        assertThat(Duration.between(Instant.now(), sesion.expiraEn()))
                .isBetween(Duration.ofMinutes(minutos).minusMinutes(1), Duration.ofMinutes(minutos));
        assertThat(sesion.inactividadMinutos()).isEqualTo(minutos);
    }

    @Entonces("se le niega el acceso al panel")
    public void se_niega_acceso() {
        assertThatThrownBy(() -> login().iniciarSesion(uidIntentado))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Cuando("se consulta el login del panel sin credenciales")
    public void login_sin_credenciales() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/auth/admin")
                .contentType(APPLICATION_JSON).content("{}")));
    }

    @Cuando("el administrador usa una sesión vencida por inactividad")
    public void sesion_vencida() throws Exception {
        String vencido = emisor.emitir("uid-muni-bdd", EmisorDeJwt.ROL_ADMIN, Duration.ofSeconds(-1)).token();
        consultarPanel(vencido);
    }

    @Cuando("un conductor con su sesión consulta el panel municipal")
    public void conductor_consulta() throws Exception {
        consultarPanel(emisor.emitirParaConductor("uid-cond-bdd").token());
    }

    @Cuando("un pasajero sin sesión consulta el panel municipal")
    public void pasajero_consulta() throws Exception {
        consultarPanel(null);
    }

    @Cuando("el administrador con su sesión consulta el panel municipal")
    public void admin_consulta() throws Exception {
        consultarPanel(login().iniciarSesion("uid-muni-bdd").token());
    }

    @Entonces("la cuenta con uid {string} no tiene contraseña almacenada")
    public void sin_contrasena(String uid) {
        assertThat(usuarios.findByFirebaseUid(uid)).get()
                .satisfies(u -> assertThat(u.getPasswordHash()).isNull());
    }

    @Y("el panel muestra todas las rutas activas")
    public void todas_las_rutas() throws Exception {
        Integer activas = jdbc.queryForObject("SELECT count(*) FROM rutas WHERE activa", Integer.class);
        contexto.ultimaRespuesta().andExpect(jsonPath("$.rutas.length()").value(activas));
    }

    private void consultarPanel(String token) throws Exception {
        var peticion = get("/api/v1/admin/servicio");
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        contexto.guardarRespuesta(mockMvc.perform(peticion));
    }

    /** Verificador de prueba: el idToken es el uid. */
    private AutenticacionDeAdministrador login() {
        return new AutenticacionDeAdministrador(
                idToken -> new IdentidadFirebase(idToken, idToken + "@muni.gt", "ecoruta"),
                usuarios, emisor, panel);
    }

    private void crearCuenta(String usuario, String rol, String uid) {
        jdbc.update("INSERT INTO usuarios (username, rol, activo, firebase_uid) VALUES (?, ?, TRUE, ?)",
                usuario, rol, uid);
    }
}
