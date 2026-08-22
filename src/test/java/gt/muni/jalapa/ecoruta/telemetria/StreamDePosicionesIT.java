package gt.muni.jalapa.ecoruta.telemetria;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-140 (HU-45): el stream de posiciones en tiempo real.
 *
 * <p>Se prueba con MockMvc y sin asyncDispatch: MockHttpServletResponse va
 * acumulando lo que el emisor escribe, asi que basta abrir el stream, provocar
 * una ingesta y leer lo escrito. Evita levantar un segundo contexto de Spring
 * con RANDOM_PORT, que es lo caro de esta suite.
 */
class StreamDePosicionesIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    private AltaDeEquipo equipoDelBusPiloto() {
        Long bus = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        return equipoService.emitir(bus, "Tableta de pruebas");
    }

    @Test
    void el_stream_es_publico_y_no_pide_credencial() throws Exception {
        // Criterio 3. Las cabeceras se fijan en el handler, antes de devolver el
        // emisor, asi que ya estan aqui.
        mockMvc.perform(get("/api/v1/telemetria/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                // ADR-008: si el proxy acumula la respuesta, el stream nunca llega.
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test
    void el_stream_se_sirve_como_text_event_stream() throws Exception {
        // Criterio 1. El Content-Type lo pone el convertidor al escribir el primer
        // evento, no en el instante en que arranca la peticion asincrona.
        AltaDeEquipo equipo = equipoDelBusPiloto();
        MvcResult stream = abrirStream();

        ingestar(equipo, 14.6400);

        assertThat(stream.getResponse().getContentType()).contains("text/event-stream");
    }

    @Test
    void al_conectarse_recibe_de_inmediato_la_posicion_vigente() throws Exception {
        // Si no, el mapa del pasajero arranca en blanco hasta el siguiente lote.
        AltaDeEquipo equipo = equipoDelBusPiloto();
        ingestar(equipo, 14.6335);

        MvcResult stream = abrirStream();

        assertThat(cuerpo(stream))
                .contains("event:posicion")
                .contains("14.6335")
                .contains("BUS-01");
    }

    @Test
    void una_posicion_ingestada_llega_a_los_suscriptores() throws Exception {
        // Criterio 2, que es el corazon de la historia.
        AltaDeEquipo equipo = equipoDelBusPiloto();
        MvcResult stream = abrirStream();
        assertThat(cuerpo(stream)).doesNotContain("14.6400");

        ingestar(equipo, 14.6400);

        assertThat(cuerpo(stream))
                .contains("event:posicion")
                .contains("14.64");
    }

    @Test
    void la_misma_posicion_llega_a_todos_los_suscriptores() throws Exception {
        // "Difunde la posicion mas reciente a TODOS los suscriptores".
        AltaDeEquipo equipo = equipoDelBusPiloto();
        MvcResult uno = abrirStream();
        MvcResult otro = abrirStream();

        ingestar(equipo, 14.6400);

        assertThat(cuerpo(uno)).contains("14.64");
        assertThat(cuerpo(otro)).contains("14.64");
    }

    @Test
    void cada_evento_lleva_id_y_tiempo_de_reconexion() throws Exception {
        // El id: es el requisito previo del Last-Event-ID de SCRUM-280, y el
        // retry: le dice a EventSource cuanto esperar antes de reconectar.
        AltaDeEquipo equipo = equipoDelBusPiloto();
        MvcResult stream = abrirStream();

        ingestar(equipo, 14.6400);

        assertThat(cuerpo(stream))
                .contains("id:")
                .contains("retry:3000");
    }

    @Test
    void un_stream_abierto_sin_ninguna_posicion_todavia_no_envia_nada() throws Exception {
        // Se queda esperando, no devuelve error ni cierra.
        MvcResult stream = abrirStream();

        assertThat(cuerpo(stream)).doesNotContain("event:posicion");
    }

    @Test
    void una_posicion_descartada_por_reloj_desfasado_no_se_difunde() throws Exception {
        AltaDeEquipo equipo = equipoDelBusPiloto();
        MvcResult stream = abrirStream();

        ingestarEn(equipo, 14.5000, Instant.now().minus(java.time.Duration.ofDays(2)));

        assertThat(cuerpo(stream)).doesNotContain("14.5");
    }

    private MvcResult abrirStream() throws Exception {
        return mockMvc.perform(get("/api/v1/telemetria/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String cuerpo(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    private void ingestar(AltaDeEquipo equipo, double latitud) throws Exception {
        ingestarEn(equipo, latitud, Instant.now());
    }

    private void ingestarEn(AltaDeEquipo equipo, double latitud, Instant cuando) throws Exception {
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": %s, "longitud": -89.9885, "velocidadKmh": 18,
                                   "timestamp": "%s"}
                                ]}""".formatted(latitud, cuando)))
                .andExpect(status().isAccepted());
    }
}
