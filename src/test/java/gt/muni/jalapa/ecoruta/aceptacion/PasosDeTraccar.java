package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class PasosDeTraccar {

    private static final String RUTA = "/api/v1/integraciones/traccar/posiciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Dado("que el dispositivo Traccar {string} está asociado al equipo del bus {string}")
    public void dispositivo_asociado(String imei, String bus) {
        Long vehiculo = vehiculos.findByIdentificador(bus).orElseThrow().getId();
        equipoService.emitir(vehiculo, "GPS 103A " + bus);
        jdbc.update("""
                INSERT INTO dispositivos_externos (identificador, equipo_id)
                SELECT ?, id FROM equipos WHERE vehiculo_id = ? AND estado = 'ACTIVO'
                """, imei, vehiculo);
    }

    @Cuando("Traccar reenvía una posición del dispositivo {string} sin la cabecera de integración")
    public void sin_cabecera(String imei) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(peticion(reenvio(1, imei, 14.6335, 10, Instant.now()))));
    }

    @Cuando("Traccar reenvía una posición del dispositivo {string} con el token {string}")
    public void con_token(String imei, String token) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(peticion(reenvio(1, imei, 14.6335, 10, Instant.now()))
                .header("X-Traccar-Token", token)));
    }

    @Cuando("Traccar reenvía la posición {long} del dispositivo {string}")
    public void reenvia(long id, String imei) throws Exception {
        enviar(reenvio(id, imei, 14.6335, 10, Instant.now()));
    }

    @Cuando("Traccar reenvía una posición del dispositivo {string} con latitud {double}")
    public void con_latitud(String imei, double latitud) throws Exception {
        enviar(reenvio(1, imei, latitud, 10, Instant.now()));
    }

    @Cuando("Traccar reenvía la posición {long} del dispositivo {string} a {int} nudos")
    public void a_nudos(long id, String imei, int nudos) throws Exception {
        enviar(reenvio(id, imei, 14.6335, nudos, Instant.now()));
    }

    @Cuando("Traccar reenvía la posición {long} del dispositivo {string} con fecha de hace {int} horas")
    public void hace_horas(long id, String imei, int horas) throws Exception {
        enviar(reenvio(id, imei, 14.6335, 10, Instant.now().minus(Duration.ofHours(horas))));
    }

    @Entonces("no se registró ninguna posición")
    public void ninguna_posicion() {
        assertThat(filas()).isZero();
    }

    @Entonces("hay {int} posición registrada")
    public void posiciones_registradas(int cantidad) {
        assertThat(filas()).isEqualTo(cantidad);
    }

    @Y("la posición vigente es del bus {string}")
    public void posicion_vigente_del_bus(String bus) {
        assertThat(jdbc.queryForObject("""
                SELECT v.identificador FROM posiciones_historicas p JOIN vehiculos v ON v.id = p.vehiculo_id
                ORDER BY p.registrado_en DESC LIMIT 1
                """, String.class)).isEqualTo(bus);
    }

    @Entonces("la velocidad registrada es {word} km\\/h")
    public void velocidad_registrada(String kmh) {
        assertThat(jdbc.queryForObject("SELECT velocidad_kmh FROM posiciones_historicas", Double.class))
                .isCloseTo(Double.parseDouble(kmh), within(0.001));
    }

    @Entonces("la respuesta resume {int} recibidas, {int} aceptadas y {int} descartadas")
    public void resumen(int recibidas, int aceptadas, int descartadas) throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(jsonPath("$.recibidas").value(recibidas))
                .andExpect(jsonPath("$.aceptadas").value(aceptadas))
                .andExpect(jsonPath("$.descartadas").value(descartadas));
    }

    @Entonces("el módulo de Traccar registra con el servicio de telemetría y sin clientes HTTP")
    public void sin_clientes_http() throws IOException {
        Path modulo = Path.of("src/main/java/gt/muni/jalapa/ecoruta/integraciones/traccar");
        String recepcion = Files.readString(modulo.resolve("servicio/RecepcionTraccar.java"));
        assertThat(recepcion).contains("telemetria.registrar(");
        try (Stream<Path> fuentes = Files.walk(modulo)) {
            fuentes.filter(p -> p.toString().endsWith(".java")).forEach(fuente -> {
                try {
                    assertThat(Files.readString(fuente))
                            .as("%s no debe llamar por HTTP", fuente)
                            .doesNotContain("RestTemplate", "WebClient", "RestClient", "HttpClient");
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
    }

    @Entonces("existe la relación entre dispositivo externo y equipo")
    public void existe_relacion() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_name = 'dispositivos_externos'
                   AND constraint_type IN ('FOREIGN KEY', 'UNIQUE')
                """, Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Y("existe la restricción única de la clave de origen")
    public void existe_restriccion() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_posicion_clave_origen'",
                Integer.class)).isEqualTo(1);
    }

    private void enviar(String cuerpo) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(peticion(cuerpo)
                .header("X-Traccar-Token", IntegracionPostgisTest.TRACCAR)));
    }

    private static MockHttpServletRequestBuilder peticion(String cuerpo) {
        return post(RUTA).contentType(APPLICATION_JSON).content(cuerpo);
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Integer.class);
    }

    private static String reenvio(long id, String imei, double latitud, int nudos, Instant fecha) {
        return """
                {"position":{"id":%d,"latitude":%s,"longitude":-89.9885,"speed":%d,"fixTime":"%s"},
                 "device":{"uniqueId":"%s"}}""".formatted(id, latitud, nudos, fecha, imei);
    }
}
