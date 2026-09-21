package gt.muni.jalapa.ecoruta.calles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-26 (HU Desarrollo-146), bloque C: el desvio se traza por las calles
 * reales de Jalapa (OpenStreetMap importado en V19/V20) con pgRouting, y no
 * como una linea recta multiplicada por un factor.
 */
class DesvioPorCallesIT extends IntegracionPostgisTest {

    /** Vertice del trazado del circuito de V6, en la 1a Calle. */
    private static final double LAT_EN_TRAZADO = 14.633161;
    private static final double LON_EN_TRAZADO = -89.985636;

    /** Unas cuadras al norte del trazado, dentro de la ciudad: hay calles. */
    private static final double LAT_DESVIADO = LAT_EN_TRAZADO + 0.0035;
    private static final double LON_DESVIADO = LON_EN_TRAZADO - 0.0012;

    @Autowired
    private RedDeCalles calles;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private ObjectMapper json;

    @Test
    void la_red_de_calles_de_jalapa_quedo_importada() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calles", Integer.class)).isGreaterThan(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calles_nodos", Integer.class)).isGreaterThan(500);
        // Las de un solo sentido llevan -1: asi pgRouting no las recorre al reves.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM calles WHERE sentido_unico AND costo_reverso <> -1", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pgrouting'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void el_camino_por_calles_es_mas_largo_que_la_linea_recta() {
        PuntoResponse desde = new PuntoResponse(LAT_DESVIADO, LON_DESVIADO);
        PuntoResponse hasta = new PuntoResponse(LAT_EN_TRAZADO, LON_EN_TRAZADO);

        RedDeCalles.Camino camino = calles.caminoMasCorto(desde, List.of(hasta)).orElseThrow();

        double recta = jdbc.queryForObject("""
                SELECT ST_Distance(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                                   ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                """, Double.class, LON_DESVIADO, LAT_DESVIADO, LON_EN_TRAZADO, LAT_EN_TRAZADO);

        // Por calles hay que rodear manzanas y respetar sentidos: sale mas largo
        // que la recta, y tambien que la vieja estimacion de recta por 1.3.
        assertThat(camino.metros()).isGreaterThan(recta * 1.3);
        assertThat(camino.trazo()).hasSizeGreaterThan(2);
        assertThat(camino.destino()).isZero();
    }

    @Test
    void entre_varios_candidatos_gana_el_del_camino_por_calles_mas_corto() {
        PuntoResponse desde = new PuntoResponse(LAT_DESVIADO, LON_DESVIADO);
        PuntoResponse unCandidato = new PuntoResponse(LAT_DESVIADO - 0.0008, LON_DESVIADO + 0.0004);
        PuntoResponse otroCandidato = new PuntoResponse(LAT_EN_TRAZADO, LON_EN_TRAZADO);

        RedDeCalles.Camino camino =
                calles.caminoMasCorto(desde, List.of(unCandidato, otroCandidato)).orElseThrow();

        double porElPrimero = calles.caminoMasCorto(desde, List.of(unCandidato))
                .map(RedDeCalles.Camino::metros).orElse(Double.MAX_VALUE);
        double porElSegundo = calles.caminoMasCorto(desde, List.of(otroCandidato))
                .map(RedDeCalles.Camino::metros).orElse(Double.MAX_VALUE);

        // Se elige por distancia real por calles, no por cercania en linea recta.
        assertThat(camino.metros()).isEqualTo(Math.min(porElPrimero, porElSegundo));
    }

    @Test
    void sin_calle_cerca_no_hay_camino_y_el_eta_vuelve_al_factor() throws Exception {
        // Fuera de la ciudad importada: ningun nodo dentro del radio.
        assertThat(calles.caminoMasCorto(new PuntoResponse(14.90, -90.40),
                List.of(new PuntoResponse(LAT_EN_TRAZADO, LON_EN_TRAZADO)))).isEmpty();

        Instant ahora = Instant.now();
        ingestar("BUS-01",
                lectura(LAT_EN_TRAZADO, LON_EN_TRAZADO, 25, ahora.minusSeconds(40)),
                lectura(14.90, -90.40, 25, ahora));

        JsonNode desvio = eta(1).get("desvio");

        assertThat(desvio.isNull()).isFalse();
        double fuera = desvio.get("metrosFueraDelTrazado").asDouble();
        double hastaVolver = desvio.get("metrosHastaReincorporar").asDouble();
        // Sin red: la vuelta es la recta por el factor configurado (1.3).
        assertThat(hastaVolver / fuera).isBetween(1.2, 1.4);
    }

    @Test
    void en_desvio_el_recorrido_estimado_pasa_por_las_calles() throws Exception {
        Instant ahora = Instant.now();
        ingestar("BUS-01",
                lectura(LAT_EN_TRAZADO, LON_EN_TRAZADO, 25, ahora.minusSeconds(40)),
                lectura(LAT_DESVIADO, LON_DESVIADO, 25, ahora));

        JsonNode eta = eta(1);

        assertThat(eta.get("estado").asText()).isEqualTo("EN_DESVIO");
        JsonNode desvio = eta.get("desvio");
        double fuera = desvio.get("metrosFueraDelTrazado").asDouble();
        double hastaVolver = desvio.get("metrosHastaReincorporar").asDouble();
        assertThat(hastaVolver).isGreaterThan(fuera);
        // El recorrido ya no es un salto recto: trae los vertices de las calles.
        assertThat(desvio.get("recorridoEstimado").size()).isGreaterThan(5);
    }

    private static String lectura(double latitud, double longitud, Integer velocidadKmh, Instant momento) {
        return """
                {"latitud": %s, "longitud": %s, "velocidadKmh": %s, "timestamp": "%s"}"""
                .formatted(latitud, longitud, velocidadKmh, momento);
    }

    private void ingestar(String identificador, String... lecturas) throws Exception {
        Long bus = vehiculos.findByIdentificador(identificador).orElseThrow().getId();
        AltaDeEquipo equipo = equipoService.emitir(bus, "Tableta IT calles");

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("{\"posiciones\": [" + Arrays.stream(lecturas).collect(Collectors.joining(","))
                                + "]}"))
                .andExpect(status().isAccepted());
    }

    private JsonNode eta(long rutaId) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v1/rutas/{id}/eta", rutaId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
