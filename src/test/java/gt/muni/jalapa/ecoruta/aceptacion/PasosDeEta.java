package gt.muni.jalapa.ecoruta.aceptacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.servicio.EtaService;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaParadaResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Traduce los pasos de {@code calcular_tiempo_estimado_de_llegada.feature} (SCRUM-166). */
public class PasosDeEta {

    /** Vertice del trazado de V6 en la 1a Calle, antes de la parada 2 (Mercado). */
    private static final double LAT_ANTES_DE_PARADA_2 = 14.633161;
    private static final double LON_ANTES_DE_PARADA_2 = -89.985636;

    /** Parada 3 (El Calvario) de V6. */
    private static final double LAT_PARADA_3 = 14.630328;
    private static final double LON_PARADA_3 = -89.993654;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private EtaService etaService;

    @Autowired
    private EtaProperties propiedades;

    private Long segundaRuta;
    private JsonNode etaRuta1;
    private JsonNode etaSegundaRuta;
    private Instant inicio;
    private EtaRutaResponse etaAntes;
    private boolean recalculo;

    @Dado("que la ruta {long} tiene trazado y paradas")
    public void la_ruta_tiene_trazado_y_paradas(long rutaId) {
        Integer paradas = jdbc.queryForObject("""
                SELECT count(*) FROM paradas p JOIN rutas r ON r.id = p.ruta_id
                 WHERE r.id = ? AND r.trazado IS NOT NULL
                """, Integer.class, rutaId);
        assertThat(paradas).isPositive();
    }

    @Dado("que el vehiculo {string} esta asignado a la ruta {long}")
    public void el_vehiculo_esta_asignado_a_la_ruta(String identificador, long rutaId) {
        asignar(identificador, rutaId);
    }

    @Dado("que el vehiculo {string} reporta posiciones recientes a {int} km\\/h antes de la parada 2")
    public void reporta_posiciones_recientes(String identificador, int kmh) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, (double) kmh, ahora.minusSeconds(20));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, (double) kmh, ahora.minusSeconds(10));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, (double) kmh, ahora.minusSeconds(1));
    }

    @Dado("que el vehiculo {string} reporta una sola posicion reciente sin velocidad")
    public void reporta_una_posicion_sin_velocidad(String identificador) {
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, null, Instant.now());
    }

    @Dado("que la ultima posicion del vehiculo {string} tiene mas de {int} segundos")
    public void la_ultima_posicion_es_vieja(String identificador, int segundos) {
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 30d,
                Instant.now().minusSeconds(segundos + 60L));
    }

    @Dado("que existe una segunda ruta con trazado y paradas")
    public void existe_una_segunda_ruta() {
        // La ruta de prueba a la Metroplaza que siembra V12.
        segundaRuta = jdbc.queryForObject(
                "SELECT id FROM rutas WHERE nombre = 'Ruta de prueba - Parque Central a Metroplaza'",
                Long.class);
        la_ruta_tiene_trazado_y_paradas(segundaRuta);
    }

    @Dado("que el vehiculo {string} esta asignado a la segunda ruta")
    public void el_vehiculo_esta_asignado_a_la_segunda_ruta(String identificador) {
        assertThat(vehiculoDeRuta(segundaRuta))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = ?",
                        Long.class, identificador));
    }

    @Dado("que el vehiculo {string} reporta posiciones recientes en la parada 2 de la segunda ruta")
    public void reporta_en_la_segunda_ruta(String identificador) {
        insertarPosicion(identificador, 14.638392, -89.987701, 20d, Instant.now());
    }

    @Dado("que el intervalo minimo de recalculo es de {int} segundos")
    public void el_intervalo_minimo_es(int segundos) {
        assertThat(propiedades.intervaloMinimoRecalculoSegundos()).isEqualTo(segundos);
    }

    @Dado("que el ETA de la ruta {long} ya se calculo")
    public void el_eta_ya_se_calculo(long rutaId) {
        inicio = Instant.now();
        assertThat(etaService.recalcularSiCorresponde(rutaId, inicio)).isTrue();
        etaAntes = etaService.consultar(rutaId);
    }

    @Cuando("llega una posicion nueva en la parada 3 a los {int} segundos")
    public void llega_posicion_nueva(int segundos) {
        insertarPosicion("BUS-01", LAT_PARADA_3, LON_PARADA_3, 30d, Instant.now());
        recalculo = etaService.recalcularSiCorresponde(1L, inicio.plusSeconds(segundos));
    }

    @Cuando("se vuelve a evaluar a los {int} segundos")
    public void se_vuelve_a_evaluar(int segundos) {
        recalculo = etaService.recalcularSiCorresponde(1L, inicio.plusSeconds(segundos));
    }

    @Entonces("el ETA no se recalcula")
    public void el_eta_no_se_recalcula() {
        assertThat(recalculo).isFalse();
        assertThat(etaService.consultar(1L)).isEqualTo(etaAntes);
    }

    @Entonces("el ETA se recalcula con la nueva posicion")
    public void el_eta_se_recalcula() {
        assertThat(recalculo).isTrue();
        assertThat(minutosDe(etaService.consultar(1L), 3)).isZero();
        assertThat(minutosDe(etaAntes, 3)).isPositive();
    }

    @Cuando("consulto el ETA de la ruta {long}")
    public void consulto_el_eta(long rutaId) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(get("/api/v1/rutas/{id}/eta", rutaId)));
    }

    @Cuando("consulto el ETA de la ruta 1 y de la segunda ruta")
    public void consulto_ambas_rutas() throws Exception {
        etaRuta1 = leer(1L);
        etaSegundaRuta = leer(segundaRuta);
    }

    @Entonces("la parada de orden {int} tiene menos minutos que la parada de orden {int}")
    public void menos_minutos(int cercana, int lejana) throws Exception {
        JsonNode eta = respuesta();
        assertThat(minutos(eta, cercana)).isLessThan(minutos(eta, lejana));
    }

    @Y("la parada de orden {int} tiene más minutos que la parada de orden {int} aunque esté más cerca en línea recta")
    public void mas_minutos_por_el_recorrido(int porRecorrido, int referencia) throws Exception {
        JsonNode eta = respuesta();
        assertThat(minutos(eta, porRecorrido)).isGreaterThan(minutos(eta, referencia));
    }

    @Entonces("las paradas pendientes son confiables")
    public void son_confiables() throws Exception {
        respuesta().get("paradas").forEach(p -> assertThat(p.get("confiable").asBoolean()).isTrue());
    }

    @Entonces("las paradas pendientes tienen minutos calculados")
    public void tienen_minutos() throws Exception {
        respuesta().get("paradas").forEach(p -> assertThat(p.get("minutos").isInt()).isTrue());
    }

    @Y("las paradas pendientes no son confiables")
    public void no_son_confiables() throws Exception {
        respuesta().get("paradas").forEach(p -> assertThat(p.get("confiable").asBoolean()).isFalse());
    }

    @Entonces("la respuesta incluye la ruta {long}, el vehiculo y la fecha de calculo")
    public void incluye_ruta_vehiculo_y_fecha(long rutaId) throws Exception {
        JsonNode eta = respuesta();
        assertThat(eta.get("rutaId").asLong()).isEqualTo(rutaId);
        assertThat(eta.get("vehiculoId").isIntegralNumber()).isTrue();
        assertThat(Instant.parse(eta.get("calculadoEn").asText())).isNotNull();
    }

    @Y("las {int} paradas del circuito traen paradaId, orden y minutos enteros")
    public void paradas_con_minutos(int cantidad) throws Exception {
        JsonNode paradas = respuesta().get("paradas");
        assertThat(paradas.size()).isEqualTo(cantidad);
        paradas.forEach(p -> {
            assertThat(p.get("paradaId").isIntegralNumber()).isTrue();
            assertThat(p.get("orden").isInt()).isTrue();
            assertThat(p.get("minutos").isInt()).isTrue();
        });
    }

    @Entonces("todas las paradas tienen minutos nulos")
    public void minutos_nulos() throws Exception {
        JsonNode paradas = respuesta().get("paradas");
        assertThat(paradas.size()).isPositive();
        paradas.forEach(p -> assertThat(p.get("minutos").isNull()).isTrue());
    }

    @Y("ninguna parada es confiable")
    public void ninguna_confiable() throws Exception {
        no_son_confiables();
    }

    @Entonces("cada respuesta usa el vehiculo de su propia ruta")
    public void cada_una_con_su_vehiculo() {
        assertThat(etaRuta1.get("vehiculoId").asLong()).isEqualTo(vehiculoDeRuta(1L));
        assertThat(etaSegundaRuta.get("vehiculoId").asLong()).isEqualTo(vehiculoDeRuta(segundaRuta));
        assertThat(etaRuta1.get("vehiculoId")).isNotEqualTo(etaSegundaRuta.get("vehiculoId"));
    }

    @Y("cada respuesta trae solo las paradas de su propia ruta")
    public void cada_una_con_sus_paradas() {
        assertThat(etaRuta1.get("paradas").size()).isEqualTo(8);
        assertThat(etaSegundaRuta.get("paradas").size()).isEqualTo(5);
        assertThat(minutos(etaSegundaRuta, 2)).isZero();
        assertThat(minutos(etaRuta1, 2)).isPositive();
    }

    @Entonces("el calculo del ETA no usa ningun cliente HTTP")
    public void no_usa_cliente_http() throws IOException {
        Path paquete = Path.of("src/main/java/gt/muni/jalapa/ecoruta/eta");
        try (Stream<Path> fuentes = Files.walk(paquete)) {
            fuentes.filter(p -> p.toString().endsWith(".java")).forEach(fuente -> {
                String codigo = leerFuente(fuente);
                assertThat(codigo)
                        .as("%s no debe llamar servicios externos", fuente)
                        .doesNotContain("RestTemplate", "WebClient", "RestClient",
                                "HttpClient", "java.net.URL", "google");
            });
        }
    }

    private void asignar(String identificador, long rutaId) {
        jdbc.update("""
                INSERT INTO vehiculos (identificador, placa)
                VALUES (?, ?) ON CONFLICT (identificador) DO NOTHING
                """, identificador, "P-" + identificador);
        jdbc.update("UPDATE vehiculos SET ruta_id = ? WHERE identificador = ?", rutaId, identificador);
    }

    private void insertarPosicion(String identificador, double latitud, double longitud,
                                  Double kmh, Instant registradoEn) {
        jdbc.update("""
                INSERT INTO posiciones_historicas (ubicacion, velocidad_kmh, registrado_en, vehiculo_id)
                SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, v.id
                  FROM vehiculos v WHERE v.identificador = ?
                """, longitud, latitud, kmh, java.sql.Timestamp.from(registradoEn), identificador);
    }

    private long vehiculoDeRuta(long rutaId) {
        return jdbc.queryForObject("SELECT id FROM vehiculos WHERE ruta_id = ?", Long.class, rutaId);
    }

    private JsonNode respuesta() throws Exception {
        return json.readTree(contexto.ultimaRespuesta().andReturn().getResponse().getContentAsString());
    }

    private JsonNode leer(long rutaId) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v1/rutas/{id}/eta", rutaId))
                .andReturn().getResponse().getContentAsString());
    }

    private static int minutos(JsonNode eta, int orden) {
        for (JsonNode parada : eta.get("paradas")) {
            if (parada.get("orden").asInt() == orden) {
                return parada.get("minutos").asInt();
            }
        }
        throw new AssertionError("La parada de orden " + orden + " no esta en el ETA");
    }

    private static int minutosDe(EtaRutaResponse eta, int orden) {
        return eta.paradas().stream()
                .filter(p -> p.orden() == orden)
                .map(EtaParadaResponse::minutos)
                .findFirst()
                .orElseThrow();
    }

    private static String leerFuente(Path fuente) {
        try {
            return Files.readString(fuente);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
