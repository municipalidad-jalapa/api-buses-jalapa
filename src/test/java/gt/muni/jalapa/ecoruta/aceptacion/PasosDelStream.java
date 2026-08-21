package gt.muni.jalapa.ecoruta.aceptacion;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Traduce los pasos de {@code stream_en_tiempo_real.feature} (SCRUM-140). */
public class PasosDelStream {

    private static final double LATITUD = 14.6335;
    private static final double LONGITUD = -89.9885;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContextoDelEscenario contexto;

    // ---------- Dado / Cuando ----------

    @Dado("un pasajero conectado al stream")
    public void un_pasajero_conectado() throws Exception {
        contexto.registrarStream(abrirStream());
    }

    @Dado("dos pasajeros conectados al stream")
    public void dos_pasajeros_conectados() throws Exception {
        contexto.registrarStream(abrirStream());
        contexto.registrarStream(abrirStream());
    }

    @Cuando("un pasajero se conecta al stream de posiciones")
    public void un_pasajero_se_conecta() throws Exception {
        contexto.registrarStream(abrirStream());
    }

    @Cuando("el equipo reporta una posición con el reloj de hace dos días")
    public void reporta_con_el_reloj_desfasado() throws Exception {
        reportar(Instant.now().minus(Duration.ofDays(2)));
    }

    // ---------- Entonces ----------

    @Entonces("el stream queda abierto")
    public void el_stream_queda_abierto() {
        assertThat(contexto.stream(0)).isNotNull();
    }

    @Entonces("el stream pide al proxy que no acumule la respuesta")
    public void el_stream_pide_que_no_acumulen() {
        // ADR-008: si el proxy acumula la respuesta, el stream nunca llega.
        assertThat(contexto.stream(0).getResponse().getHeader("X-Accel-Buffering"))
                .isEqualTo("no");
    }

    @Entonces("el pasajero recibe un evento de posición")
    public void el_pasajero_recibe_un_evento() throws Exception {
        assertThat(cuerpo(0)).contains("event:posicion");
    }

    @Entonces("el pasajero recibe la posición del {string}")
    public void el_pasajero_recibe_la_posicion_del(String identificador) throws Exception {
        assertThat(cuerpo(0)).contains("event:posicion").contains(identificador);
    }

    @Entonces("los dos pasajeros reciben la posición")
    public void los_dos_pasajeros_reciben() throws Exception {
        assertThat(cuerpo(0)).contains("event:posicion");
        assertThat(cuerpo(1)).contains("event:posicion");
    }

    @Entonces("el pasajero no recibe ningún evento de posición")
    public void el_pasajero_no_recibe_nada() throws Exception {
        assertThat(cuerpo(0)).doesNotContain("event:posicion");
    }

    // ---------- utilidades ----------

    private MvcResult abrirStream() throws Exception {
        return mockMvc.perform(get("/api/v1/telemetria/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();
    }

    private String cuerpo(int indice) throws Exception {
        return contexto.stream(indice).getResponse().getContentAsString();
    }

    private void reportar(Instant cuando) throws Exception {
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + contexto.equipo().credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": %s, "longitud": %s, "velocidadKmh": 18,
                                   "timestamp": "%s"}
                                ]}""".formatted(LATITUD, LONGITUD, cuando)))
                .andExpect(status().isAccepted());
    }
}
