package gt.muni.jalapa.ecoruta.eta.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-166 (HU Desarrollo-71): GET /api/v1/rutas/{rutaId}/eta de punta a punta,
 * con las posiciones entrando por la ingesta real para que el recalculo lo
 * dispare el evento de telemetria. Cada prueba manda un solo lote: el limite de
 * frecuencia ignoraria un segundo lote inmediato.
 */
class EtaRutaIT extends IntegracionPostgisTest {

    /** Vertice del trazado de V6 en la 1a Calle, antes de la parada 2 (Mercado). */
    private static final double LAT_ANTES_DE_PARADA_2 = 14.633161;
    private static final double LON_ANTES_DE_PARADA_2 = -89.985636;

    /** Vertice siguiente del trazado, ~125 m mas adelante. */
    private static final double LAT_SIGUIENTE = 14.632733;
    private static final double LON_SIGUIENTE = -89.986725;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private ObjectMapper json;

    @Test
    void la_distancia_sigue_el_recorrido_y_no_la_linea_recta() throws Exception {
        Instant ahora = Instant.now();
        ingestar("BUS-01", lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 30, ahora));

        JsonNode eta = eta(1);

        assertThat(eta.get("rutaId").asLong()).isEqualTo(1);
        assertThat(eta.get("vehiculoId").isNull()).isFalse();
        assertThat(eta.get("calculadoEn").isNull()).isFalse();
        assertThat(eta.get("estado").asText()).isEqualTo("EN_RUTA");
        assertThat(eta.get("desvio").isNull()).isTrue();
        assertThat(eta.get("paradas").size()).isEqualTo(8);
        assertThat(minutos(eta, 2)).isLessThan(minutos(eta, 3));
        // La parada 8 esta a unos 200 m en linea recta, pero por el recorrido le
        // falta casi toda la vuelta: si se midiera en recta saldria en 1 minuto.
        assertThat(minutos(eta, 8)).isGreaterThan(minutos(eta, 5));
        eta.get("paradas").forEach(p -> assertThat(p.get("confiable").asBoolean()).isTrue());
    }

    @Test
    void sin_velocidad_de_ningun_tipo_usa_la_de_respaldo_y_no_es_confiable() throws Exception {
        ingestar("BUS-01", lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, null, Instant.now()));

        JsonNode eta = eta(1);

        eta.get("paradas").forEach(p -> {
            assertThat(p.get("minutos").isInt()).isTrue();
            assertThat(p.get("confiable").asBoolean()).isFalse();
        });
    }

    @Test
    void sin_velocidad_reportada_la_deduce_de_las_posiciones_y_es_confiable() throws Exception {
        Instant ahora = Instant.now();
        ingestar("BUS-01",
                lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, null, ahora.minusSeconds(15)),
                lectura(LAT_SIGUIENTE, LON_SIGUIENTE, null, ahora));   // ~125 m en 15 s = 30 km/h

        JsonNode eta = eta(1);

        assertThat(eta.get("estado").asText()).isEqualTo("EN_RUTA");
        eta.get("paradas").forEach(p -> assertThat(p.get("confiable").asBoolean()).isTrue());
    }

    @Test
    void detenido_fuera_de_parada_por_mas_del_maximo_no_proyecta_un_numero() throws Exception {
        Instant ahora = Instant.now();
        ingestar("BUS-01",
                lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 0, ahora.minusSeconds(360)),
                lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 0, ahora.minusSeconds(180)),
                lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 0, ahora));

        JsonNode eta = eta(1);

        assertThat(eta.get("estado").asText()).isEqualTo("DETENIDO_FUERA_DE_PARADA");
        eta.get("paradas").forEach(p -> assertThat(p.get("minutos").isNull()).isTrue());
    }

    @Test
    void en_desvio_recalcula_por_la_reincorporacion_y_devuelve_el_recorrido_estimado() throws Exception {
        Instant ahora = Instant.now();
        ingestar("BUS-01",
                lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 25, ahora.minusSeconds(40)),
                // ~330 m al norte de la 1a Calle: fuera del trazado.
                lectura(LAT_ANTES_DE_PARADA_2 + 0.003, LON_ANTES_DE_PARADA_2 - 0.001, 25, ahora));

        JsonNode eta = eta(1);

        assertThat(eta.get("estado").asText()).isEqualTo("EN_DESVIO");
        JsonNode desvio = eta.get("desvio");
        assertThat(desvio.get("metrosFueraDelTrazado").asInt()).isGreaterThan(60);
        assertThat(desvio.get("metrosHastaReincorporar").asInt())
                .isGreaterThanOrEqualTo(desvio.get("metrosFueraDelTrazado").asInt());
        assertThat(desvio.get("reincorporacion").get("latitud").isNumber()).isTrue();
        // Lo recorrido desde la salida, el punto de vuelta y el resto del trazado.
        assertThat(desvio.get("recorridoEstimado").size()).isGreaterThan(3);
        assertThat(desvio.get("recorridoEstimado").get(0).get("latitud").asDouble())
                .isEqualTo(LAT_ANTES_DE_PARADA_2);
        eta.get("paradas").forEach(p -> {
            assertThat(p.get("minutos").isInt()).isTrue();   // circuito: ninguna queda sin estimar
            assertThat(p.get("confiable").asBoolean()).isFalse();
        });
    }

    @Test
    void con_la_posicion_vieja_no_se_inventa_un_numero() throws Exception {
        ingestar("BUS-01", lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 30,
                Instant.now().minusSeconds(600)));

        JsonNode eta = eta(1);

        assertThat(eta.get("estado").asText()).isEqualTo("SIN_DATOS");
        assertThat(eta.get("paradas").size()).isEqualTo(8);
        eta.get("paradas").forEach(p -> {
            assertThat(p.get("minutos").isNull()).isTrue();
            assertThat(p.get("confiable").asBoolean()).isFalse();
        });
    }

    @Test
    void sin_posiciones_todas_las_paradas_salen_no_disponibles() throws Exception {
        JsonNode eta = eta(1);

        eta.get("paradas").forEach(p -> assertThat(p.get("minutos").isNull()).isTrue());
    }

    @Test
    void dos_rutas_se_calculan_a_la_vez_cada_una_con_su_bus() throws Exception {
        // Ruta de prueba a la Metroplaza y su BUS-02, sembrados por V12.
        Vehiculo bus1 = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        Vehiculo bus2 = vehiculos.findByIdentificador("BUS-02").orElseThrow();
        long ruta1 = bus1.getRutaId();
        long ruta2 = bus2.getRutaId();

        ingestar("BUS-01", lectura(LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 30, Instant.now()));
        // BUS-02 en su parada 2 (Avenida Chipilapa - 4a Calle).
        ingestar("BUS-02", lectura(14.638392, -89.987701, 20, Instant.now()));

        JsonNode eta1 = eta(ruta1);
        JsonNode eta2 = eta(ruta2);

        assertThat(eta1.get("vehiculoId").asLong()).isEqualTo(bus1.getId());
        assertThat(eta2.get("vehiculoId").asLong()).isEqualTo(bus2.getId());
        assertThat(eta1.get("paradas").size()).isEqualTo(8);
        assertThat(eta2.get("paradas").size()).isEqualTo(5);
        assertThat(minutos(eta2, 2)).isZero();
        assertThat(minutos(eta2, 3)).isPositive();
        // El BUS-02 no mueve el ETA de la otra ruta: en la suya, la parada 2 no esta en 0.
        assertThat(minutos(eta1, 2)).isPositive();
    }

    @Test
    void una_ruta_que_no_existe_responde_404() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/999999/eta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private static String lectura(double latitud, double longitud, Integer velocidadKmh, Instant timestamp) {
        return """
                {"latitud": %s, "longitud": %s, "velocidadKmh": %s, "timestamp": "%s"}"""
                .formatted(latitud, longitud, velocidadKmh, timestamp);
    }

    /** Un solo lote: una sola publicacion del evento de posicion. */
    private void ingestar(String identificador, String... lecturas) throws Exception {
        Long bus = vehiculos.findByIdentificador(identificador).orElseThrow().getId();
        AltaDeEquipo equipo = equipoService.emitir(bus, "Tableta IT eta");

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("{\"posiciones\": [" + Arrays.stream(lecturas).collect(Collectors.joining(","))
                                + "]}"))
                .andExpect(status().isAccepted());
    }

    private JsonNode eta(long rutaId) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/rutas/{id}/eta", rutaId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private static int minutos(JsonNode eta, int orden) {
        for (JsonNode parada : eta.get("paradas")) {
            if (parada.get("orden").asInt() == orden) {
                return parada.get("minutos").asInt();
            }
        }
        throw new AssertionError("La parada de orden " + orden + " no esta en el ETA");
    }
}
