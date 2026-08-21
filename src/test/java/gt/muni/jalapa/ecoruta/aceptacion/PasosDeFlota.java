package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.telemetria.repositorio.PosicionHistoricaRepository;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Traduce los pasos de {@code vehiculo_y_su_equipo_a_bordo.feature} (SCRUM-143). */
public class PasosDeFlota {

    private static final String ADMIN = "token-de-pruebas-con-mas-de-32-caracteres";
    private static final double LATITUD = 14.6335;
    private static final double LONGITUD = -89.9885;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private PosicionHistoricaRepository posiciones;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private ContextoDelEscenario contexto;

    // ---------- Dado ----------

    @Dado("el vehículo {string} registrado en la flota")
    public void el_vehiculo_registrado(String identificador) {
        // BUS-01 lo siembra V5; los demas los crea el escenario.
        vehiculos.findByIdentificador(identificador).orElseGet(() ->
                vehiculos.save(new Vehiculo(identificador, "P-" + identificador.hashCode())));
    }

    @Dado("un equipo con credencial vigente en el {string}")
    public void un_equipo_con_credencial_en_el(String identificador) {
        contexto.registrarEquipo(equipoService.emitir(
                idDe(identificador), "Tableta " + identificador));
    }

    @Y("ese equipo reportó {int} posiciones")
    public void ese_equipo_reporto_n_posiciones(int cuantas) throws Exception {
        for (int i = 0; i < cuantas; i++) {
            reportar(contexto.equipo()).andExpect(status().isAccepted());
        }
    }

    // ---------- Cuando ----------

    @Cuando("el administrador intenta dar de alta otro equipo en el mismo bus")
    public void intenta_dar_de_alta_otro_equipo() throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/admin/equipos")
                .header("X-Admin-Token", ADMIN)
                .contentType(APPLICATION_JSON)
                .content("{\"vehiculoId\": %d, \"etiqueta\": \"Segunda tableta\"}"
                        .formatted(idDe("BUS-01")))));
    }

    @Cuando("cada equipo reporta su posición")
    public void cada_equipo_reporta_su_posicion() throws Exception {
        for (int i = 0; i < contexto.cuantosEquipos(); i++) {
            reportar(contexto.equipo(i)).andExpect(status().isAccepted());
        }
    }

    @Cuando("ese equipo reporta una posición diciendo que es del {string}")
    public void reporta_diciendo_que_es_de_otro_bus(String identificador) throws Exception {
        // El vehiculo sale de la credencial, nunca del cuerpo: mandar vehiculoId
        // aqui no debe cambiar nada.
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(contexto.equipo()))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": %d, "posiciones": [
                                  {"latitud": %s, "longitud": %s, "timestamp": "%s"}
                                ]}""".formatted(idDe(identificador), LATITUD, LONGITUD, Instant.now())))
                .andExpect(status().isAccepted());
    }

    @Cuando("el administrador cambia el equipo a bordo del {string}")
    public void el_administrador_cambia_el_equipo(String identificador) throws Exception {
        String cuerpo = mockMvc.perform(
                        post("/api/v1/admin/vehiculos/" + idDe(identificador) + "/equipos")
                                .header("X-Admin-Token", ADMIN)
                                .contentType(APPLICATION_JSON)
                                .content("{\"etiqueta\": \"Tableta de repuesto\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contexto.guardarCredencialCruda(
                cuerpo.replaceAll(".*\"credencial\":\"([^\"]+)\".*", "$1"));
    }

    @Y("el equipo nuevo reporta {int} posición")
    public void el_equipo_nuevo_reporta(int cuantas) throws Exception {
        for (int i = 0; i < cuantas; i++) {
            mockMvc.perform(post("/api/v1/telemetria/posiciones")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + contexto.credencialCruda())
                            .contentType(APPLICATION_JSON)
                            .content(lote()))
                    .andExpect(status().isAccepted());
        }
    }

    // ---------- Entonces ----------

    @Entonces("la operación se rechaza explicando que ya hay un equipo activo")
    public void la_operacion_se_rechaza() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("BUS-01")));
    }

    @Entonces("cada posición queda atribuida a su propio vehículo")
    public void cada_posicion_queda_en_su_vehiculo() {
        assertThat(posiciones.countByVehiculoId(idDe("BUS-01"))).isEqualTo(1);
        assertThat(posiciones.countByVehiculoId(idDe("BUS-02"))).isEqualTo(1);
    }

    @Entonces("la posición queda atribuida al {string}, no al que dijo el equipo")
    public void la_posicion_queda_en_el_bus_de_la_credencial(String identificador) {
        assertThat(posiciones.countByVehiculoId(idDe(identificador))).isEqualTo(1);
        assertThat(posiciones.countByVehiculoId(idDe("BUS-02"))).isZero();
    }

    @Entonces("el {string} conserva sus {int} posiciones")
    public void el_bus_conserva_sus_posiciones(String identificador, int cuantas) {
        assertThat(posiciones.countByVehiculoId(idDe(identificador))).isEqualTo(cuantas);
    }

    @Y("las posiciones viejas siguen atribuidas al equipo que las reportó")
    public void las_posiciones_viejas_siguen_con_su_equipo() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM posiciones_historicas WHERE equipo_id = ?",
                Long.class, contexto.equipo().equipoId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM posiciones_historicas WHERE vehiculo_id IS NULL",
                Long.class)).isZero();
    }

    @Y("el equipo viejo ya no puede reportar")
    public void el_equipo_viejo_ya_no_reporta() throws Exception {
        reportar(contexto.equipo()).andExpect(status().isUnauthorized());
    }

    // ---------- utilidades ----------

    private Long idDe(String identificador) {
        return vehiculos.findByIdentificador(identificador).orElseThrow().getId();
    }

    private static String bearer(AltaDeEquipo alta) {
        return "Bearer " + alta.credencial().credencialCompleta();
    }

    private org.springframework.test.web.servlet.ResultActions reportar(AltaDeEquipo equipo)
            throws Exception {
        return mockMvc.perform(post("/api/v1/telemetria/posiciones")
                .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                .contentType(APPLICATION_JSON)
                .content(lote()));
    }

    private static String lote() {
        return """
                {"posiciones": [
                  {"latitud": %s, "longitud": %s, "velocidadKmh": 20, "timestamp": "%s"}
                ]}""".formatted(LATITUD, LONGITUD, Instant.now());
    }
}
