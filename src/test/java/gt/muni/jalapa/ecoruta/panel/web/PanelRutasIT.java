package gt.muni.jalapa.ecoruta.panel.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-79: el panel municipal carga todas las rutas activas en una sola
 * llamada a GET /api/v1/panel/rutas.
 */
class PanelRutasIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private ObjectMapper json;

    @Test
    void sin_token_de_admin_responde_401_con_el_formato_ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/panel/rutas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/panel/rutas"));
    }

    @Test
    void lista_las_rutas_activas_con_su_bus_y_sin_posicion() throws Exception {
        Long bus01 = idDe("BUS-01");
        Long bus02 = idDe("BUS-02");

        mockMvc.perform(panel())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutas", hasSize(2)))
                .andExpect(jsonPath("$.rutas[0].rutaId").value(1))
                .andExpect(jsonPath("$.rutas[0].vehiculoId").value(bus01.intValue()))
                .andExpect(jsonPath("$.rutas[0].posicion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rutas[0].transmitiendo").value(false))
                .andExpect(jsonPath("$.rutas[1].rutaId").value(2))
                .andExpect(jsonPath("$.rutas[1].vehiculoId").value(bus02.intValue()));
    }

    @Test
    void vehiculo_y_posicion_van_null_si_la_ruta_no_tiene_bus_asignado() throws Exception {
        Long rutaOriginal = jdbc.queryForObject(
                "SELECT ruta_id FROM vehiculos WHERE identificador = 'BUS-02'", Long.class);
        jdbc.update("UPDATE vehiculos SET ruta_id = NULL WHERE identificador = 'BUS-02'");
        try {
            JsonNode ruta = rutaDe(leerPanel(), 2L);

            assertThat(ruta.get("vehiculoId").isNull()).isTrue();
            assertThat(ruta.get("posicion").isNull()).isTrue();
            assertThat(ruta.get("transmitiendo").asBoolean()).isFalse();
        } finally {
            jdbc.update("UPDATE vehiculos SET ruta_id = ? WHERE identificador = 'BUS-02'",
                    rutaOriginal);
        }
    }

    @Test
    void marca_transmitiendo_true_cuando_la_posicion_es_reciente() throws Exception {
        Instant ahora = Instant.now();
        ingestarPosicion("BUS-01", 14.6335, -89.9885, ahora);

        JsonNode ruta = rutaDe(leerPanel(), 1L);

        assertThat(ruta.get("transmitiendo").asBoolean()).isTrue();
        assertThat(ruta.get("posicion").get("latitud").asDouble()).isEqualTo(14.6335);
        assertThat(ruta.get("posicion").get("longitud").asDouble()).isEqualTo(-89.9885);
        assertThat(Instant.parse(ruta.get("posicion").get("registradaEn").asText()))
                .isEqualTo(ahora.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void marca_transmitiendo_false_cuando_la_posicion_es_vieja() throws Exception {
        Instant haceDiezMinutos = Instant.now().minus(Duration.ofMinutes(10));
        ingestarPosicion("BUS-01", 14.6400, -89.9885, haceDiezMinutos);

        JsonNode ruta = rutaDe(leerPanel(), 1L);

        assertThat(ruta.get("posicion").isNull()).isFalse();
        assertThat(ruta.get("transmitiendo").asBoolean()).isFalse();
    }

    @Test
    void reservas_salen_en_cero_cuando_no_hay_ninguna() throws Exception {
        JsonNode filas = rutaDe(leerPanel(), 1L).get("reservasPorParada");

        assertThat(filas.size()).isEqualTo(8);
        filas.forEach(fila -> assertThat(fila.get("activas").asInt()).isZero());
    }

    @Test
    void el_conteo_por_parada_solo_suma_reservas_activa_y_renovada() throws Exception {
        insertarEspera("it-activa", 1L, "ACTIVA");
        insertarEspera("it-renovada", 1L, "RENOVADA");
        insertarEspera("it-abordo", 1L, "ABORDO");
        insertarEspera("it-cancelada", 1L, "CANCELADA");

        JsonNode filas = rutaDe(leerPanel(), 1L).get("reservasPorParada");

        assertThat(conteoDeParada(filas, 1L)).isEqualTo(2);
        assertThat(conteoDeParada(filas, 2L)).isZero();
    }

    private RequestBuilder panel() {
        return get("/api/v1/panel/rutas")
                .header(AdminBootstrapFilter.CABECERA, ADMIN);
    }

    private JsonNode leerPanel() throws Exception {
        String cuerpo = mockMvc.perform(panel())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private void ingestarPosicion(String identificador, double latitud, double longitud,
                                  Instant cuando) throws Exception {
        Long bus = idDe(identificador);
        AltaDeEquipo equipo = equipoService.emitir(bus, "Tableta IT panel");

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": %s, "longitud": %s, "velocidadKmh": 18,
                                   "timestamp": "%s"}
                                ]}""".formatted(latitud, longitud, cuando)))
                .andExpect(status().isAccepted());
    }

    private void insertarEspera(String dispositivoId, long paradaId, String estado) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, now(), now() + interval '30 minutes')
                """, dispositivoId, paradaId, estado);
    }

    private Long idDe(String identificador) {
        return vehiculos.findByIdentificador(identificador).orElseThrow().getId();
    }

    private static JsonNode rutaDe(JsonNode respuesta, long rutaId) {
        for (JsonNode ruta : respuesta.get("rutas")) {
            if (ruta.get("rutaId").asLong() == rutaId) {
                return ruta;
            }
        }
        throw new AssertionError("La ruta " + rutaId + " no aparece en el panel");
    }

    private static int conteoDeParada(JsonNode filas, long paradaId) {
        for (JsonNode fila : filas) {
            if (fila.get("paradaId").asLong() == paradaId) {
                return fila.get("activas").asInt();
            }
        }
        throw new AssertionError("La parada " + paradaId + " no aparece en el panel");
    }
}
