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
import static org.assertj.core.api.Assertions.within;
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

    @Dado("que el vehiculo {string} reporta dos posiciones sin velocidad avanzando sobre el trazado")
    public void dos_posiciones_sin_velocidad(String identificador) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, null, ahora.minusSeconds(15));
        // Siguiente vertice del trazado, ~125 m despues: ~30 km/h.
        insertarPosicion(identificador, 14.632733, -89.986725, null, ahora);
    }

    @Dado("que la parada de orden {int} tiene una reserva activa")
    public void la_parada_tiene_una_reserva(int orden) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                SELECT 'bdd-eta', p.id, 'ACTIVA', now(), now() + interval '30 minutes'
                  FROM paradas p WHERE p.ruta_id = 1 AND p.orden = ?
                """, orden);
    }

    @Dado("que el vehiculo {string} lleva {int} minutos detenido fuera de cualquier parada")
    public void detenido_fuera_de_parada(String identificador, int minutos) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 30d,
                ahora.minusSeconds(minutos * 60L + 20));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 0d,
                ahora.minusSeconds(minutos * 60L));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 0d, ahora);
    }

    @Dado("que el vehiculo {string} sale del trazado a unos 300 metros de la 1a Calle")
    public void sale_del_trazado(String identificador) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 25d, ahora.minusSeconds(40));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2 + 0.003, LON_ANTES_DE_PARADA_2 - 0.001, 25d, ahora);
    }

    @Entonces("el estado del bus es {string}")
    public void el_estado_es(String estado) throws Exception {
        assertThat(respuesta().get("estado").asText()).isEqualTo(estado);
    }

    @Entonces("los minutos a la parada de orden {int} incluyen la espera con reserva de la parada de orden {int}")
    public void incluyen_la_espera(int destino, int intermedia) throws Exception {
        // Se reproduce el calculo con la distancia medida por PostGIS: 36 km/h = 10 m/s.
        Double metros = jdbc.queryForObject("""
                SELECT (ST_LineLocatePoint(r.trazado, p.ubicacion)
                        - ST_LineLocatePoint(r.trazado, ST_SetSRID(ST_MakePoint(?, ?), 4326)))
                       * ST_Length(r.trazado::geography)
                  FROM rutas r JOIN paradas p ON p.ruta_id = r.id
                 WHERE r.id = 1 AND p.orden = ?
                """, Double.class, LON_ANTES_DE_PARADA_2, LAT_ANTES_DE_PARADA_2, destino);
        int esperado = (int) Math.ceil((metros / 10 + propiedades.esperaConReservaSegundos()) / 60);
        int sinEspera = (int) Math.ceil(metros / 10 / 60);

        JsonNode eta = respuesta();
        assertThat(minutos(eta, destino)).isEqualTo(esperado);
        assertThat(minutos(eta, destino)).isGreaterThanOrEqualTo(sinEspera);
        assertThat(minutos(eta, intermedia)).isLessThan(minutos(eta, destino));
    }

    @Y("el desvio trae el punto de reincorporacion y el recorrido estimado")
    public void el_desvio_trae_reincorporacion() throws Exception {
        JsonNode desvio = respuesta().get("desvio");
        assertThat(desvio.get("metrosFueraDelTrazado").asInt()).isGreaterThan(propiedades.desvioMetros());
        assertThat(desvio.get("reincorporacion").get("latitud").isNumber()).isTrue();
        assertThat(desvio.get("recorridoEstimado").size()).isGreaterThan(3);
    }

    @Y("el recorrido estimado empieza donde el bus dejo el trazado")
    public void empieza_donde_salio() throws Exception {
        JsonNode primero = respuesta().get("desvio").get("recorridoEstimado").get(0);
        assertThat(primero.get("latitud").asDouble()).isEqualTo(LAT_ANTES_DE_PARADA_2);
        assertThat(primero.get("longitud").asDouble()).isEqualTo(LON_ANTES_DE_PARADA_2);
    }

    // --- SCRUM-26, bloque C: el desvio trazado por las calles de OSM ---------

    @Dado("que el vehiculo {string} sale del trazado unas cuadras al norte de la 1a Calle")
    public void sale_del_trazado_hacia_las_calles(String identificador) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 25d,
                ahora.minusSeconds(40));
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2 + 0.0035,
                LON_ANTES_DE_PARADA_2 - 0.0012, 25d, ahora);
    }

    @Dado("que el vehiculo {string} sale del trazado donde no hay ninguna calle importada")
    public void sale_del_trazado_sin_calles(String identificador) {
        Instant ahora = Instant.now();
        insertarPosicion(identificador, LAT_ANTES_DE_PARADA_2, LON_ANTES_DE_PARADA_2, 25d,
                ahora.minusSeconds(40));
        insertarPosicion(identificador, 14.90, -90.40, 25d, ahora);
    }

    @Y("la vuelta al trazado es mas larga que la linea recta")
    public void la_vuelta_es_mas_larga() throws Exception {
        JsonNode desvio = respuesta().get("desvio");
        double fuera = desvio.get("metrosFueraDelTrazado").asDouble();
        double hastaVolver = desvio.get("metrosHastaReincorporar").asDouble();
        assertThat(hastaVolver).isGreaterThan(fuera * propiedades.factorDesvio());
    }

    @Y("el recorrido estimado sigue las calles")
    public void el_recorrido_sigue_las_calles() throws Exception {
        assertThat(respuesta().get("desvio").get("recorridoEstimado").size()).isGreaterThan(5);
    }

    @Y("la vuelta al trazado se estimo con el factor")
    public void la_vuelta_con_factor() throws Exception {
        JsonNode desvio = respuesta().get("desvio");
        double fuera = desvio.get("metrosFueraDelTrazado").asDouble();
        double hastaVolver = desvio.get("metrosHastaReincorporar").asDouble();
        assertThat(hastaVolver / fuera).isCloseTo(propiedades.factorDesvio(), within(0.1));
    }

    @Entonces("la red de calles de Jalapa esta importada en la base")
    public void la_red_esta_importada() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calles", Integer.class)).isGreaterThan(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calles_nodos", Integer.class)).isGreaterThan(500);
    }

    @Y("las vias de un solo sentido no se pueden recorrer al reves")
    public void sentido_respetado() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM calles WHERE sentido_unico AND costo_reverso <> -1", Integer.class))
                .isZero();
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
