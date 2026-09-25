package gt.muni.jalapa.ecoruta.demanda.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-284 (HU Desarrollo-123): la pantalla del pasajero carga con una sola
 * llamada a GET /api/v1/rutas/{rutaId}/resumen.
 *
 * <p>Se comprueba que lo que trae el resumen es lo mismo que ya devuelven las
 * consultas individuales de ruta y de posicion: si divergen, la pantalla
 * mostraria datos distintos segun por donde entre.
 */
class ResumenRutaIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private ObjectMapper json;

    @Test
    void devuelve_200_con_ruta_posicion_y_reservas_en_una_sola_respuesta() throws Exception {
        ingestarPosicion(14.6335, -89.9885, 18);

        mockMvc.perform(get("/api/v1/rutas/1/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruta.id").value(1))
                .andExpect(jsonPath("$.ruta.paradas", hasSize(8)))
                .andExpect(jsonPath("$.ruta.paradas[0].orden").value(1))
                .andExpect(jsonPath("$.ruta.paradas[7].orden").value(8))
                .andExpect(jsonPath("$.posicionActual.latitud").exists())
                .andExpect(jsonPath("$.posicionActual.capturadoEn").exists())
                .andExpect(jsonPath("$.reservasActivas.porParada", hasSize(8)))
                .andExpect(jsonPath("$.reservasActivas.calculadoEn").exists());
    }

    @Test
    void devuelve_200_con_la_posicion_vacia_cuando_el_bus_aun_no_ha_reportado() throws Exception {
        // limpiarDatosDePrueba ya trunco posiciones_historicas: no hay ninguna.
        JsonNode resumen = leerOk(get("/api/v1/rutas/1/resumen"));

        assertThat(resumen.get("posicionActual").isNull()).isTrue();
        assertThat(resumen.get("ruta").get("paradas").size()).isEqualTo(8);
        assertThat(resumen.get("reservasActivas").get("porParada").size()).isEqualTo(8);
    }

    @Test
    void cada_parada_aparece_con_conteo_cero_cuando_no_tiene_reservas() throws Exception {
        JsonNode filas = leerOk(get("/api/v1/rutas/1/resumen"))
                .get("reservasActivas").get("porParada");

        assertThat(filas.size()).isEqualTo(8);
        filas.forEach(fila -> assertThat(fila.get("reservasActivas").asInt()).isZero());
    }

    @Test
    void el_conteo_por_parada_solo_suma_reservas_activa_y_renovada() throws Exception {
        insertarEspera("it-activa", 1L, "ACTIVA");
        insertarEspera("it-renovada", 1L, "RENOVADA");
        insertarEspera("it-abordo", 1L, "ABORDO");
        insertarEspera("it-cancelada", 1L, "CANCELADA");
        insertarEspera("it-expirada", 2L, "EXPIRADA");

        JsonNode filas = leerOk(get("/api/v1/rutas/1/resumen"))
                .get("reservasActivas").get("porParada");

        assertThat(conteoDeParada(filas, 1L)).isEqualTo(2);
        assertThat(conteoDeParada(filas, 2L)).isZero();
    }

    @Test
    void los_datos_de_la_ruta_coinciden_con_el_endpoint_individual() throws Exception {
        JsonNode resumen = leerOk(get("/api/v1/rutas/1/resumen"));
        JsonNode rutaSola = leerOk(get("/api/v1/rutas/1"));

        JsonNode ruta = resumen.get("ruta");
        assertThat(ruta.get("id")).isEqualTo(rutaSola.get("id"));
        assertThat(ruta.get("nombre")).isEqualTo(rutaSola.get("nombre"));
        assertThat(ruta.get("paradas")).isEqualTo(rutaSola.get("paradas"));
        assertThat(ruta.get("trazado")).isEqualTo(rutaSola.get("trazado"));
    }

    @Test
    void la_posicion_coincide_con_el_endpoint_individual_de_telemetria() throws Exception {
        ingestarPosicion(14.6400, -89.9885, 21);

        JsonNode posEnResumen = leerOk(get("/api/v1/rutas/1/resumen")).get("posicionActual");
        JsonNode posSola = leerOk(get("/api/v1/telemetria/posicion"));

        assertThat(posEnResumen.get("latitud")).isEqualTo(posSola.get("latitud"));
        assertThat(posEnResumen.get("longitud")).isEqualTo(posSola.get("longitud"));
        assertThat(posEnResumen.get("velocidadKmh")).isEqualTo(posSola.get("velocidadKmh"));
        // El resumen lo llama capturadoEn; telemetria, timestamp. Mismo Instant,
        // misma cadena ISO-8601 UTC.
        assertThat(posEnResumen.get("capturadoEn")).isEqualTo(posSola.get("timestamp"));
    }

    @Test
    void una_ruta_que_no_existe_responde_404_con_el_formato_ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/999999/resumen"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/rutas/999999/resumen"));
    }

    @Test
    void un_rutaId_con_formato_invalido_responde_400_con_el_formato_ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/abc/resumen"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/rutas/abc/resumen"));
    }

    private void ingestarPosicion(double latitud, double longitud, int velocidadKmh) throws Exception {
        Long bus = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        AltaDeEquipo equipo = equipoService.emitir(bus, "Tableta IT resumen");

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": %s, "longitud": %s, "velocidadKmh": %s,
                                   "timestamp": "%s"}
                                ]}""".formatted(latitud, longitud, velocidadKmh, Instant.now())))
                .andExpect(status().isAccepted());
    }

    private void insertarEspera(String dispositivoId, long paradaId, String estado) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, now(), now() + interval '30 minutes')
                """, dispositivoId, paradaId, estado);
    }

    private JsonNode leerOk(RequestBuilder request) throws Exception {
        String cuerpo = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private static int conteoDeParada(JsonNode filas, long paradaId) {
        for (JsonNode fila : filas) {
            if (fila.get("paradaId").asLong() == paradaId) {
                return fila.get("reservasActivas").asInt();
            }
        }
        throw new AssertionError("La parada " + paradaId + " no aparece en el resumen");
    }
}
