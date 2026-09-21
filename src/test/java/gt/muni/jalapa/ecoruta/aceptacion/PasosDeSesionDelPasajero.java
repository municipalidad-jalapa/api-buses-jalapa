package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.identidad.servicio.IdentidadFirebase;
import gt.muni.jalapa.ecoruta.pasajeros.PasajeroProperties;
import gt.muni.jalapa.ecoruta.pasajeros.repositorio.PasajeroRepository;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero.Sesion;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero.Vinculacion;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pasos de {@code sesion_del_pasajero.feature} (SCRUM-26, bloque B).
 *
 * <p>Firebase no se sustituye con {@code @MockitoBean} (levantaria otro
 * contexto): el servicio se arma con un verificador de prueba donde el idToken
 * es el uid. El resto del mecanismo es el real.
 */
public class PasosDeSesionDelPasajero {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private PasajeroRepository pasajeros;

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private PasajeroProperties propiedades;

    private Sesion sesion;
    private String misReservas;
    private Vinculacion vinculacion;

    @Before("@bloque-B")
    public void limpiar() {
        jdbc.update("DELETE FROM opiniones");
        jdbc.update("DELETE FROM pasajeros");
    }

    @Cuando("la cuenta de Google {string} inicia sesión como pasajero")
    @Dado("que la cuenta de Google {string} inició sesión como pasajero")
    public void inicia_sesion(String uid) {
        sesion = servicio().iniciar(uid);
    }

    @Dado("que el navegador {string} tiene {int} reservas y {int} opinión como invitado")
    public void datos_de_invitado(String navegador, int reservas, int opiniones) {
        for (int i = 0; i < reservas; i++) {
            jdbc.update("""
                    INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                    VALUES (?, 1, 'CANCELADA', now(), now() + interval '5 minutes')
                    """, navegador);
        }
        for (int i = 0; i < opiniones; i++) {
            jdbc.update("INSERT INTO opiniones (tipo, ruta_id, dispositivo_id, estrellas) VALUES ('CALIFICACION', 1, ?, 4)",
                    navegador);
        }
    }

    @Cuando("vincula el navegador {string}")
    public void vincula(String navegador) {
        Long id = Long.valueOf(emisor.leer(sesion.token(), SesionDePasajero.ROL).orElseThrow().subject());
        vinculacion = servicio().vincular(id, navegador);
    }

    @Entonces("recibe una sesión del sistema con rol {string}")
    public void recibe_sesion(String rol) {
        assertThat(sesion.rol()).isEqualTo(rol);
        assertThat(emisor.leer(sesion.token(), rol)).isPresent();
    }

    @Entonces("se vincularon {int} reservas y {int} opinión")
    public void se_vincularon(int reservas, int opiniones) {
        assertThat(vinculacion.reservasVinculadas()).isEqualTo(reservas);
        assertThat(vinculacion.opinionesVinculadas()).isEqualTo(opiniones);
    }

    @Dado("que el navegador {string} tiene una reserva vigente como invitado")
    public void reserva_vigente(String navegador) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, 1, 'ACTIVA', now(), now() + interval '5 minutes')
                """, navegador);
    }

    /** Otro telefono = la misma cuenta, sin el identificador anonimo del navegador. */
    @Cuando("consulta sus reservas desde otro teléfono")
    public void consulta_sus_reservas() throws Exception {
        misReservas = mockMvc.perform(get("/api/v1/reservas/mias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion.token()))
                .andReturn().getResponse().getContentAsString();
    }

    @Entonces("solo aparece la reserva del navegador {string}")
    public void solo_aparece(String navegador) {
        assertThat(misReservas).contains("\"id\":" + reservaDe(navegador));
        assertThat(misReservas.split("\"id\":")).hasSize(2);
    }

    @Cuando("cancela desde otro teléfono la reserva del navegador {string}")
    public void cancela_desde_otro_telefono(String navegador) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(delete("/api/v1/reservas/" + reservaDe(navegador))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion.token())));
    }

    private Long reservaDe(String navegador) {
        return jdbc.queryForObject(
                "SELECT id FROM registros_espera WHERE dispositivo_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, navegador);
    }

    @Cuando("el pasajero consulta el panel del conductor")
    public void consulta_conductor() throws Exception {
        consultar("/api/v1/conductor/ruta");
    }

    @Cuando("el pasajero consulta el panel municipal")
    public void consulta_municipal() throws Exception {
        consultar("/api/v1/admin/servicio");
    }

    @Entonces("las reservas y las opiniones aceptan no tener cuenta")
    public void cuenta_opcional() {
        for (String tabla : new String[]{"registros_espera", "opiniones"}) {
            assertThat(jdbc.queryForObject("""
                    SELECT is_nullable FROM information_schema.columns
                     WHERE table_name = ? AND column_name = 'pasajero_id'
                    """, String.class, tabla)).as(tabla).isEqualTo("YES");
        }
    }

    private void consultar(String ruta) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get(ruta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion.token())));
    }

    private SesionDePasajero servicio() {
        return new SesionDePasajero(idToken -> new IdentidadFirebase(idToken, idToken + "@gmail.com", "ecoruta"),
                pasajeros, emisor, propiedades, jdbc);
    }
}
