package gt.muni.jalapa.ecoruta.integraciones.traccar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.herramientas.SimuladorGps;
import gt.muni.jalapa.ecoruta.integraciones.traccar.TraccarProperties.UnidadVelocidad;
import gt.muni.jalapa.ecoruta.integraciones.traccar.servicio.LecturaTraccar;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de punta a punta TK103 -&gt; Traccar -&gt; adaptador -&gt; telemetria.
 * Lenta: descarga traccar/traccar:6.15.3. No corre en {@code mvn test}
 * salvo {@code TRACCAR_E2E=true}.
 */
@EnabledIfEnvironmentVariable(named = "TRACCAR_E2E", matches = "true")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ecoruta.admin.bootstrap-token=" + IntegracionGpsTraccarIT.ADMIN,
        "ecoruta.integraciones.traccar.token=" + IntegracionGpsTraccarIT.TOKEN})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class IntegracionGpsTraccarIT {

    static final String ADMIN = "token-de-pruebas-con-mas-de-32-caracteres";
    static final String TOKEN = "traccar-e2e-token-con-mas-de-32-chars";
    static final String IMEI_A = "860000000000001";
    static final String IMEI_B = "860000000000002";
    static final String IMAGEN = "traccar/traccar:6.15.3";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres"));

    @LocalServerPort
    int puertoApp;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EquipoService equipos;
    @Autowired
    VehiculoRepository vehiculos;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    ObjectMapper json;

    GenericContainer<?> traccar;
    HttpServer listener;
    JsonNode reenvioCapturado;
    String reenvioCrudo;
    Long idBusA;
    Long idBusB;
    String hallazgoC5b = "(pendiente de ejecutar)";
    String hallazgoC6 = "(pendiente de ejecutar)";
    String hallazgoValid = "(pendiente de ejecutar)";

    @BeforeAll
    void arrancarCadena() throws Exception {
        listener = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        List<String> cuerpos = new CopyOnWriteArrayList<>();
        listener.createContext("/", intercambio -> {
            byte[] buf = intercambio.getRequestBody().readAllBytes();
            cuerpos.add(new String(buf, StandardCharsets.UTF_8));
            byte[] ok = "{\"recibidas\":1,\"aceptadas\":1,\"descartadas\":0}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(202, ok.length);
            intercambio.getResponseBody().write(ok);
            intercambio.close();
        });
        listener.start();
        int puertoCaptura = listener.getAddress().getPort();
        org.testcontainers.Testcontainers.exposeHostPorts(puertoApp, puertoCaptura);

        traccar = levantarTraccar("http://host.testcontainers.internal:" + puertoCaptura + "/");
        emitirFija(IMEI_A, 14.633500, -89.988500, Instant.now(), true, 18.52);
        Instant limite = Instant.now().plusSeconds(25);
        while (cuerpos.isEmpty() && Instant.now().isBefore(limite)) {
            TimeUnit.MILLISECONDS.sleep(400);
        }
        assertThat(cuerpos)
                .as("Traccar debio reenviar un JSON al listener temporal")
                .isNotEmpty();
        reenvioCrudo = cuerpos.getFirst();
        reenvioCapturado = json.readTree(reenvioCrudo);
        compararContrato(reenvioCapturado);
        traccar.stop();

        traccar = levantarTraccar(
                "http://host.testcontainers.internal:" + puertoApp + "/api/v1/integraciones/traccar/posiciones");
    }

    @AfterAll
    void apagar() {
        if (traccar != null) {
            traccar.stop();
        }
        if (listener != null) {
            listener.stop(0);
        }
    }

    @BeforeEach
    void sembrarFlota() {
        jdbc.execute("TRUNCATE posiciones_historicas RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE dispositivos_externos RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE equipos RESTART IDENTITY CASCADE");

        idBusA = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        idBusB = vehiculos.findByIdentificador("BUS-02").orElseThrow().getId();
        Long eqA = equipos.emitir(idBusA, "GPS A prueba e2e").equipoId();
        Long eqB = equipos.emitir(idBusB, "GPS B prueba e2e").equipoId();
        jdbc.update("INSERT INTO dispositivos_externos (identificador, equipo_id) VALUES (?, ?)", IMEI_A, eqA);
        jdbc.update("INSERT INTO dispositivos_externos (identificador, equipo_id) VALUES (?, ?)", IMEI_B, eqB);
    }

    @Test
    @Order(1)
    @DisplayName("C1 EMULADO: emisor -> Traccar -> adaptador registra clave_origen traccar:% del bus A")
    void criterio_1_emulado_la_cadena_completa_registra_la_posicion_del_bus_a() throws Exception {
        int antes = filas();
        emitirPorRuta(IMEI_A, 0);
        esperarFilas(antes + 1);

        var fila = jdbc.queryForMap("""
                SELECT vehiculo_id, clave_origen, ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon
                  FROM posiciones_historicas
                 ORDER BY id DESC LIMIT 1
                """);
        assertThat((Long) fila.get("vehiculo_id")).isEqualTo(idBusA);
        assertThat((String) fila.get("clave_origen")).startsWith("traccar:");
        assertThat(((Number) fila.get("lat")).doubleValue()).isBetween(14.5, 14.8);
        assertThat(((Number) fila.get("lon")).doubleValue()).isBetween(-90.2, -89.8);
    }

    @Test
    @Order(2)
    void criterio_2_la_posicion_es_del_vehiculo_correcto_y_no_del_otro() throws Exception {
        emitirPorRuta(IMEI_A, 0);
        esperarFilas(1);

        ResponseEntity<String> deA = rest.getForEntity(
                "/api/v1/telemetria/posicion?vehiculoId=" + idBusA, String.class);
        ResponseEntity<String> deB = rest.getForEntity(
                "/api/v1/telemetria/posicion?vehiculoId=" + idBusB, String.class);

        assertThat(deA.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpoA = json.readTree(deA.getBody());
        assertThat(cuerpoA.get("vehiculo").asText()).isEqualTo("BUS-01");
        assertThat(cuerpoA.get("latitud").asDouble()).isBetween(14.5, 14.8);

        if (deB.getStatusCode().value() == 204 || deB.getBody() == null || deB.getBody().isBlank()) {
            return;
        }
        JsonNode cuerpoB = json.readTree(deB.getBody());
        assertThat(cuerpoB.get("vehiculo").asText()).isNotEqualTo("BUS-01");
        assertThat(cuerpoB.get("latitud").asDouble()).isNotEqualTo(cuerpoA.get("latitud").asDouble());
    }

    @Test
    @Order(3)
    void criterio_3_el_stream_emite_el_evento_posicion_al_cliente_suscrito() throws Exception {
        CapturaSse sse = CapturaSse.abrir(puertoApp);
        try {
            emitirPorRuta(IMEI_A, 0);
            String lote = sse.esperarEvento(20);
            assertThat(lote).contains("event:posicion").contains("BUS-01");
        } finally {
            sse.cerrar();
        }
    }

    @Test
    @Order(4)
    @DisplayName("hallazgo 3: N posiciones distintas del mismo bus quedan en N filas")
    void n_posiciones_distintas_del_mismo_bus_quedan_registradas() throws Exception {
        // Evidencia del bug traccar/traccar#4529: el reenvio trae position.id=0
        // y, si LecturaTraccar lo trata como id valido, las N lecturas colisionan
        // en clave_origen=traccar:0 y solo queda una fila.
        int n = 3;
        Instant base = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        double[] lats = {14.6300, 14.6310, 14.6320};
        double[] lons = {-89.9800, -89.9810, -89.9820};
        for (int i = 0; i < n; i++) {
            emitirFija(IMEI_A, lats[i], lons[i], base.plusSeconds(i * 2L), true, 10);
            TimeUnit.SECONDS.sleep(2);
        }
        esperarFilas(n);
        assertThat(filas()).isEqualTo(n);

        ResponseEntity<String> vigente = rest.getForEntity(
                "/api/v1/telemetria/posicion?vehiculoId=" + idBusA, String.class);
        assertThat(vigente.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(vigente.getBody());
        assertThat(cuerpo.get("vehiculo").asText()).isEqualTo("BUS-01");
        assertThat(cuerpo.get("latitud").asDouble()).isCloseTo(lats[n - 1], org.assertj.core.data.Offset.offset(0.0005));
        assertThat(cuerpo.get("longitud").asDouble()).isCloseTo(lons[n - 1], org.assertj.core.data.Offset.offset(0.0005));
    }

    @Test
    @Order(5)
    void criterio_5_el_mismo_payload_no_duplica_fila_ni_evento() throws Exception {
        CapturaSse sse = CapturaSse.abrir(puertoApp);
        try {
            TimeUnit.MILLISECONDS.sleep(400);
            int eventosAntes = sse.eventos();
            int filasAntes = filas();

            ResponseEntity<String> primera = postAdaptador(reenvioCrudo, TOKEN);
            ResponseEntity<String> segunda = postAdaptador(reenvioCrudo, TOKEN);

            assertThat(json.readTree(primera.getBody()).get("aceptadas").asInt()).isEqualTo(1);
            assertThat(json.readTree(segunda.getBody()).get("aceptadas").asInt()).isZero();
            assertThat(json.readTree(segunda.getBody()).get("descartadas").asInt()).isEqualTo(1);
            assertThat(filas()).isEqualTo(filasAntes + 1);

            TimeUnit.MILLISECONDS.sleep(800);
            assertThat(sse.eventos() - eventosAntes)
                    .as("el segundo POST no debe emitir otro evento posicion")
                    .isEqualTo(1);
        } finally {
            sse.cerrar();
        }

        // C5b: el mismo fix por TCP dos veces. Traccar reenvia id=0; el adaptador
        // usa traccar:<uniqueId>:<epoch> y la segunda queda descartada.
        Instant fijo = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String claveNueva = "traccar:" + IMEI_A + ":" + fijo.toEpochMilli();
        int filas = filas();
        emitirFija(IMEI_A, 14.640000, -89.980000, fijo, true, 10);
        TimeUnit.SECONDS.sleep(5);
        emitirFija(IMEI_A, 14.640000, -89.980000, fijo, true, 10);
        TimeUnit.SECONDS.sleep(5);
        int despues = filas();
        List<String> claves = jdbc.queryForList(
                "SELECT clave_origen FROM posiciones_historicas WHERE clave_origen LIKE 'traccar:%' ORDER BY id",
                String.class);
        assertThat(despues).isEqualTo(filas + 1);
        assertThat(claves.stream().filter(claveNueva::equals).count()).isEqualTo(1);
        hallazgoC5b = "Mismo fixTime por TCP: filas " + filas + " -> " + despues
                + ", claves=" + claves + ". Una fila con " + claveNueva + ".";
        System.out.println("HALLAZGO C5b: " + hallazgoC5b);
    }

    @Test
    @Order(6)
    void criterio_6_fuera_de_la_ventana_de_12h_queda_en_descartadas() throws Exception {
        int antes = filas();
        emitirPorRuta(IMEI_A, 13);
        TimeUnit.SECONDS.sleep(6);
        int viaTraccar = filas();
        boolean traccarFiltro = viaTraccar == antes;

        Instant vieja = Instant.now().minus(Duration.ofHours(13));
        String cuerpo = """
                {"position":{"id":900001,"deviceId":1,"latitude":14.6335,"longitude":-89.9885,\
                "speed":5,"fixTime":"%s","deviceTime":"%s"},\
                "device":{"id":1,"uniqueId":"%s","name":"BUS-01"}}
                """.formatted(vieja, vieja, IMEI_A);
        ResponseEntity<String> resp = postAdaptador(cuerpo, TOKEN);
        JsonNode resumen = json.readTree(resp.getBody());
        assertThat(resumen.get("descartadas").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(filas()).isEqualTo(viaTraccar);

        hallazgoC6 = (traccarFiltro
                ? "Via Traccar no nacio fila (el adaptador descarta en silencio; ver log ventana). "
                : "Traccar reenvio y nacio fila (inesperado). ")
                + "Se repitio DIRECTO contra el adaptador: descartadas="
                + resumen.get("descartadas").asInt() + ".";
        System.out.println("HALLAZGO C6: " + hallazgoC6);
    }

    @Test
    @Order(7)
    void extras_token_dispositivo_y_validez() throws Exception {
        String ahora = Instant.now().toString();
        String valido = """
                {"position":{"id":900010,"deviceId":1,"latitude":14.63,"longitude":-89.98,\
                "speed":1,"fixTime":"%s","deviceTime":"%s"},\
                "device":{"id":1,"uniqueId":"%s"}}""".formatted(ahora, ahora, IMEI_A);

        assertThat(postAdaptador(valido, "token-incorrecto-con-mas-de-32-chars!!").getStatusCode().value())
                .isEqualTo(401);

        String desconocido = valido.replace(IMEI_A, "999999999999999");
        assertThat(postAdaptador(desconocido, TOKEN).getStatusCode().value()).isEqualTo(422);

        int antes = filas();
        emitirFija(IMEI_A, 0.0, 0.0, Instant.now(), false, 0);
        TimeUnit.SECONDS.sleep(6);
        int despues = filas();
        hallazgoValid = "Validez V y coordenadas 0,0: filas " + antes + " -> " + despues
                + ". El adaptador no mira position.valid; 0,0 esta en rango y se registra si llega.";
        System.out.println("HALLAZGO valid/0,0: " + hallazgoValid);
        // No se afirma el resultado: es hallazgo, no criterio.
    }

    // --- soporte -------------------------------------------------------------

    private GenericContainer<?> levantarTraccar(String forwardUrl) throws Exception {
        Path xml = Files.createTempFile("traccar-e2e-", ".xml");
        String plantilla = new String(getClass().getResourceAsStream("/traccar/traccar.xml.plantilla")
                .readAllBytes(), StandardCharsets.UTF_8);
        Files.writeString(xml, plantilla.replace("__FORWARD_URL__", forwardUrl)
                .replace("__TRACCAR_TOKEN__", TOKEN));
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(IMAGEN))
                .withExposedPorts(8082, 5001, 5002)
                .withCopyToContainer(MountableFile.forHostPath(xml), "/opt/traccar/conf/traccar.xml")
                .withAccessToHost(true)
                .waitingFor(Wait.forHttp("/api/server").forPort(8082).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3));
        c.start();
        return c;
    }

    private void emitirPorRuta(String imei, double antiguedadHoras) {
        SimuladorGps.Opciones o = new SimuladorGps.Opciones();
        o.api = "http://127.0.0.1:" + puertoApp;
        o.modo = "traccar";
        o.traccarHost = "127.0.0.1";
        o.traccarPuerto = traccar.getMappedPort(5002);
        o.protocolo = "tk103";
        o.imei = imei;
        o.mensajes = 1;
        o.credencial = "no-se-usa-en-modo-traccar";
        o.intervalo = 0.3;
        o.velocidad = 300;
        o.antiguedadHoras = antiguedadHoras;
        SimuladorGps.correr(o);
    }

    private void emitirFija(String imei, double lat, double lon, Instant cuando,
                            boolean fijo, double kmh) throws Exception {
        String msg = SimuladorGps.mensajeTk103(imei, cuando, lat, lon, kmh, fijo);
        SimuladorGps.enviarTcp("127.0.0.1", traccar.getMappedPort(5002), "tk103", imei, msg);
    }

    private ResponseEntity<String> postAdaptador(String cuerpo, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Traccar-Token", token);
        return rest.exchange("/api/v1/integraciones/traccar/posiciones",
                HttpMethod.POST, new HttpEntity<>(cuerpo, h), String.class);
    }

    private int filas() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Integer.class);
        return n == null ? 0 : n;
    }

    private void esperarFilas(int minimo) throws InterruptedException {
        Instant limite = Instant.now().plusSeconds(25);
        while (Instant.now().isBefore(limite)) {
            if (filas() >= minimo) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(400);
        }
        throw new AssertionError("No llegaron " + minimo + " filas a posiciones_historicas (hay " + filas() + ")");
    }

    private void compararContrato(JsonNode nodo) throws Exception {
        assertThat(nodo.has("position")).isTrue();
        assertThat(nodo.has("device")).isTrue();
        JsonNode p = nodo.get("position");
        JsonNode d = nodo.get("device");
        System.out.println("JSON real de Traccar (reenvio): " + nodo);
        System.out.println("Campos extra en position: " + nombres(p));
        assertThat(p.has("id")).isTrue();
        assertThat(p.has("latitude")).isTrue();
        assertThat(p.has("longitude")).isTrue();
        assertThat(p.has("speed")).isTrue();
        assertThat(p.hasNonNull("fixTime") || p.hasNonNull("deviceTime")).isTrue();
        assertThat(d.has("uniqueId")).isTrue();
        ReenvioTraccar reenvio = json.treeToValue(nodo, ReenvioTraccar.class);
        LecturaTraccar lectura = LecturaTraccar.de(reenvio, UnidadVelocidad.NUDOS);
        assertThat(lectura.dispositivo()).isEqualTo(IMEI_A);
        assertThat(lectura.lectura().claveOrigen()).startsWith("traccar:");
    }

    private static List<String> nombres(JsonNode nodo) {
        List<String> n = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(n::add);
        return n;
    }

    private static final class CapturaSse {
        private final HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private final CopyOnWriteArrayList<String> eventos = new CopyOnWriteArrayList<>();
        private final StringBuilder bruto = new StringBuilder();
        private volatile boolean seguir = true;
        private Thread hilo;
        private InputStream in;

        static CapturaSse abrir(int puerto) throws Exception {
            CapturaSse c = new CapturaSse();
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + puerto + "/api/v1/telemetria/stream"))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = c.http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            c.in = resp.body();
            c.hilo = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.in, StandardCharsets.UTF_8))) {
                    String linea;
                    while (c.seguir && (linea = r.readLine()) != null) {
                        c.bruto.append(linea).append('\n');
                        if (linea.startsWith("event:") && linea.contains("posicion")) {
                            c.eventos.add(linea);
                        }
                    }
                } catch (Exception ignored) {
                    // el test cierra el stream
                }
            }, "sse-e2e");
            c.hilo.setDaemon(true);
            c.hilo.start();
            TimeUnit.MILLISECONDS.sleep(300);
            return c;
        }

        String esperarEvento(int segundos) throws InterruptedException {
            Instant limite = Instant.now().plusSeconds(segundos);
            while (Instant.now().isBefore(limite)) {
                if (bruto.toString().contains("event:posicion")
                        || bruto.toString().contains("event: posicion")) {
                    return bruto.toString();
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
            throw new AssertionError("SSE sin evento posicion. Recibido:\n" + bruto);
        }

        int eventos() {
            return eventos.size();
        }

        void cerrar() {
            seguir = false;
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
                // ya cerrado
            }
        }
    }
}
