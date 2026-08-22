package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Pero;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traduce los pasos de {@code credencial_del_equipo_a_bordo.feature} a llamadas
 * reales contra la API.
 *
 * <p>Los pasos hablan el idioma de la historia -- "el equipo reporta su
 * posición", "el administrador revoca la credencial" -- y toda la mecanica de
 * HTTP queda aqui abajo.
 */
public class PasosDeCredencialDelEquipo {

    /** Debe coincidir con el de IntegracionPostgisTest. */
    private static final String ADMIN = "token-de-pruebas-con-mas-de-32-caracteres";
    private static final String SECRETO_FALSO = "0123456789012345678901234567890123456789012";

    /** Una parada de la ruta de Jalapa. El valor exacto no es asunto del escenario. */
    private static final double LATITUD = 14.6335;
    private static final double LONGITUD = -89.9885;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private RegistroDelEscenario registro;

    // ---------- Dado ----------

    @Dado("un equipo {string} con credencial vigente")
    public void un_equipo_con_credencial_vigente(String etiqueta) {
        contexto.registrarEquipo(equipoService.emitir(busPiloto(), etiqueta));
    }

    /** Va en otro bus: un vehiculo no admite dos equipos activos a la vez. */
    @Dado("otro equipo {string} con credencial vigente")
    public void otro_equipo_con_credencial_vigente(String etiqueta) {
        Long otroBus = vehiculos.save(new Vehiculo("BUS-02", "P-222DDD")).getId();
        contexto.registrarEquipo(equipoService.emitir(otroBus, etiqueta));
    }

    private Long busPiloto() {
        return vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
    }

    @Dado("el equipo ya reportó su posición correctamente")
    public void el_equipo_ya_reporto_su_posicion() throws Exception {
        reportar(contexto.equipo(), LATITUD, LONGITUD).andExpect(status().isAccepted());
        assertThat(cuantasPosiciones()).isEqualTo(1);
    }

    // ---------- Cuando ----------

    @Cuando("el administrador da de alta el equipo {string}")
    public void el_administrador_da_de_alta_el_equipo(String etiqueta) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/admin/equipos")
                .header("X-Admin-Token", ADMIN)
                .contentType(APPLICATION_JSON)
                .content("{\"vehiculoId\": %d, \"etiqueta\": \"%s\"}"
                        .formatted(busPiloto(), etiqueta))));
    }

    @Cuando("el equipo reporta su posición")
    public void el_equipo_reporta_su_posicion() throws Exception {
        contexto.guardarRespuesta(reportar(contexto.equipo(), LATITUD, LONGITUD));
    }

    @Cuando("alguien reporta una posición sin credencial")
    public void alguien_reporta_sin_credencial() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/telemetria/posiciones")
                .contentType(APPLICATION_JSON)
                .content(lote(LATITUD, LONGITUD))));
    }

    @Cuando("alguien reporta una posición con la credencial {string}")
    public void alguien_reporta_con_la_credencial(String credencial) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/telemetria/posiciones")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credencial)
                .contentType(APPLICATION_JSON)
                .content(lote(LATITUD, LONGITUD))));
    }

    @Cuando("el administrador revoca la credencial del equipo")
    public void el_administrador_revoca_la_credencial() throws Exception {
        revocar(contexto.equipo());
    }

    @Cuando("el administrador revoca la credencial del primer equipo")
    public void el_administrador_revoca_la_del_primero() throws Exception {
        revocar(contexto.equipo(0));
    }

    @Y("el equipo vuelve a reportar con la misma credencial")
    public void el_equipo_vuelve_a_reportar() throws Exception {
        contexto.guardarRespuesta(reportar(contexto.equipo(), LATITUD, LONGITUD));
    }

    @Y("alguien intenta reportar con esa credencial adulterada")
    public void alguien_reporta_con_credencial_adulterada() throws Exception {
        // Mismo codigo publico, secreto cambiado: el caso donde mas tienta escribir
        // el token completo en un log de rechazo.
        String adulterada = contexto.equipo().credencial().codigoPublico() + "." + SECRETO_FALSO;
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterada)
                        .contentType(APPLICATION_JSON)
                        .content(lote(LATITUD, LONGITUD)))
                .andExpect(status().isUnauthorized());
    }

    @Cuando("el equipo reporta su posición mandando la credencial en la URL")
    public void el_equipo_reporta_con_la_credencial_en_la_url() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/telemetria/posiciones")
                .param("token", contexto.equipo().credencial().credencialCompleta())
                .contentType(APPLICATION_JSON)
                .content(lote(LATITUD, LONGITUD))));
    }

    // ---------- Entonces ----------

    @Entonces("la ingesta responde {int}")
    public void la_ingesta_responde(int estado) throws Exception {
        contexto.ultimaRespuesta().andExpect(status().is(estado));
    }

    @Entonces("se emite una credencial con el formato del equipo a bordo")
    public void se_emite_una_credencial_con_el_formato() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credencial")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "^eq_[A-Za-z0-9_-]{12}\\.[A-Za-z0-9_-]{43}$")));
    }

    @Y("la credencial no está ligada a ninguna cuenta de usuario")
    public void la_credencial_no_esta_ligada_a_ninguna_cuenta() {
        // Se comprueba en el esquema, no de palabra: equipos no tiene FK a usuarios.
        assertThat(jdbc.queryForList("""
                SELECT ccu.table_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.constraint_column_usage ccu
                    ON tc.constraint_name = ccu.constraint_name
                 WHERE tc.table_name = 'equipos' AND tc.constraint_type = 'FOREIGN KEY'
                """, String.class)).doesNotContain("usuarios");
    }

    @Entonces("el listado de equipos no vuelve a mostrar el secreto")
    public void el_listado_no_muestra_el_secreto() throws Exception {
        String alta = contexto.ultimaRespuesta().andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String credencial = alta.replaceAll(".*\"credencial\":\"([^\"]+)\".*", "$1");
        String secreto = credencial.substring(credencial.indexOf('.') + 1);

        String listado = mockMvc.perform(get("/api/v1/admin/equipos")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listado).doesNotContain(secreto).doesNotContain("$2a$");
    }

    @Y("la posición reportada queda registrada a nombre de ese equipo")
    public void la_posicion_queda_a_nombre_del_equipo() {
        assertThat(jdbc.queryForObject("SELECT equipo_id FROM posiciones_historicas", Long.class))
                .isEqualTo(contexto.equipo().equipoId());
    }

    @Y("el cuerpo del error tiene el formato uniforme de la API")
    public void el_cuerpo_tiene_el_formato_ApiError() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Y("no se guardó ninguna posición")
    public void no_se_guardo_ninguna_posicion() {
        assertThat(cuantasPosiciones()).isZero();
    }

    @Y("no se guardó ninguna posición nueva")
    public void no_se_guardo_ninguna_posicion_nueva() {
        // El escenario ya reporto una correcta antes de revocar.
        assertThat(cuantasPosiciones()).isEqualTo(1);
    }

    @Entonces("el primer equipo ya no puede reportar")
    public void el_primer_equipo_ya_no_reporta() throws Exception {
        reportar(contexto.equipo(0), LATITUD, LONGITUD).andExpect(status().isUnauthorized());
    }

    @Pero("el segundo equipo sigue reportando con normalidad")
    public void el_segundo_equipo_sigue_reportando() throws Exception {
        reportar(contexto.equipo(1), LATITUD, LONGITUD).andExpect(status().isAccepted());
    }

    @Entonces("el secreto no aparece en ninguna línea de registro")
    public void el_secreto_no_aparece_en_los_registros() {
        assertThat(registro.texto())
                .doesNotContain(contexto.equipo().credencial().secreto())
                .doesNotContain(contexto.equipo().credencial().credencialCompleta());
    }

    @Pero("el código público del equipo sí aparece")
    public void el_codigo_publico_si_aparece() {
        assertThat(registro.texto())
                .contains(contexto.equipo().credencial().codigoPublico());
    }

    @Y("el secreto no aparece en la respuesta")
    public void el_secreto_no_aparece_en_la_respuesta() throws Exception {
        String cuerpo = contexto.ultimaRespuesta().andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(contexto.equipo().credencial().secreto());
    }

    // ---------- utilidades ----------

    private org.springframework.test.web.servlet.ResultActions reportar(
            AltaDeEquipo equipo, double latitud, double longitud) throws Exception {
        return mockMvc.perform(post("/api/v1/telemetria/posiciones")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + equipo.credencial().credencialCompleta())
                .contentType(APPLICATION_JSON)
                .content(lote(latitud, longitud)));
    }

    private void revocar(AltaDeEquipo equipo) throws Exception {
        mockMvc.perform(post("/api/v1/admin/equipos/" + equipo.equipoId() + "/revocacion")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(status().isNoContent());
    }

    private long cuantasPosiciones() {
        return jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Long.class);
    }

    private static String lote(double latitud, double longitud) {
        return """
                {"posiciones": [
                  {"latitud": %s, "longitud": %s, "velocidadKmh": 18, "timestamp": "%s"}
                ]}""".formatted(latitud, longitud, Instant.now());
    }
}
