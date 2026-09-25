package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.opiniones.dominio.TipoOpinion;
import gt.muni.jalapa.ecoruta.opiniones.servicio.LimiteDeOpinionesExcedido;
import gt.muni.jalapa.ecoruta.opiniones.servicio.OpinionService;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.CrearOpinionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-26, rechazo de QA: el limite de opiniones se evadia cambiando
 * {@code X-Dispositivo-Id} en cada peticion, porque ni las opiniones ni la
 * sesion del pasajero pasaban por el limite por IP.
 *
 * <p>Cupo general y por dispositivo altisimos a proposito: lo que se prueba es
 * el cupo estricto de {@link RutasPublicas.Cupo#ESTRICTO}, y que el conteo por
 * navegador del servicio aguanta envios simultaneos.
 */
@TestPropertySource(properties = {
        "ecoruta.rate-limit.por-ip-capacidad=1000",
        "ecoruta.rate-limit.por-dispositivo-capacidad=1000",
        "ecoruta.rate-limit.por-ip-estricto-capacidad=5",
        "ecoruta.rate-limit.por-ip-estricto-ventana-segundos=600"
})
class LimiteDeEnviosEvasionIT extends IntegracionPostgisTest {

    private static final String OPINION = "{\"tipo\":\"comentario\",\"rutaId\":1,\"texto\":\"Rafaga\"}";

    @Autowired
    private OpinionService opiniones;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM opiniones");
    }

    @Test
    void una_rafaga_con_un_identificador_distinto_en_cada_peticion_se_corta_por_ip() throws Exception {
        RequestPostProcessor ip = desdeIp("10.20.0.1");
        int creadas = 0;
        int rechazadas = 0;

        for (int i = 0; i < 50; i++) {
            int estado = mockMvc.perform(post("/api/v1/opiniones").with(ip)
                            .header("X-Dispositivo-Id", "evasion-" + i)
                            .contentType(APPLICATION_JSON).content(OPINION))
                    .andReturn().getResponse().getStatus();
            if (estado == 201) {
                creadas++;
            } else if (estado == 429) {
                rechazadas++;
            }
        }

        assertThat(creadas).isEqualTo(5);
        assertThat(rechazadas).isEqualTo(45);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opiniones", Integer.class)).isEqualTo(5);
    }

    @Test
    void el_429_por_ip_trae_el_formato_de_error_y_retry_after() throws Exception {
        RequestPostProcessor ip = desdeIp("10.20.0.2");
        for (int i = 0; i < 5; i++) {
            opinar(ip, "formato-" + i).andExpect(status().isCreated());
        }

        opinar(ip, "formato-nuevo")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/opiniones"));
    }

    @Test
    void otra_ip_no_hereda_el_cupo_agotado() throws Exception {
        RequestPostProcessor agotada = desdeIp("10.20.0.3");
        for (int i = 0; i < 5; i++) {
            opinar(agotada, "agotada-" + i).andExpect(status().isCreated());
        }
        opinar(agotada, "agotada-extra").andExpect(status().isTooManyRequests());

        opinar(desdeIp("10.20.0.4"), "otra-ip").andExpect(status().isCreated());
    }

    @Test
    void iniciar_sesion_como_pasajero_tambien_tiene_cupo_estricto() throws Exception {
        RequestPostProcessor ip = desdeIp("10.20.0.5");
        for (int i = 0; i < 5; i++) {
            int estado = mockMvc.perform(post("/api/v1/sesion/pasajero").with(ip)
                            .contentType(APPLICATION_JSON).content("{\"idToken\":\"falso\"}"))
                    .andReturn().getResponse().getStatus();
            assertThat(estado).isNotEqualTo(429);
        }

        mockMvc.perform(post("/api/v1/sesion/pasajero").with(ip)
                        .contentType(APPLICATION_JSON).content("{\"idToken\":\"falso\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void vincular_tambien_tiene_cupo_estricto() throws Exception {
        RequestPostProcessor ip = desdeIp("10.20.0.6");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/sesion/pasajero/vincular").with(ip)
                            .header("X-Dispositivo-Id", "vincular-" + i))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/sesion/pasajero/vincular").with(ip)
                        .header("X-Dispositivo-Id", "vincular-extra"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void agotar_las_opiniones_no_impide_iniciar_sesion() throws Exception {
        RequestPostProcessor ip = desdeIp("10.20.0.7");
        for (int i = 0; i < 5; i++) {
            opinar(ip, "separado-" + i).andExpect(status().isCreated());
        }
        opinar(ip, "separado-extra").andExpect(status().isTooManyRequests());

        int estado = mockMvc.perform(post("/api/v1/sesion/pasajero").with(ip)
                        .contentType(APPLICATION_JSON).content("{\"idToken\":\"falso\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(estado).isNotEqualTo(429);
    }

    @Test
    void envios_simultaneos_del_mismo_navegador_no_rebasan_el_limite() throws Exception {
        // ecoruta.opiniones.limite-envios = 5. Sin serializar el conteo, varios
        // hilos leen "4" a la vez y todos insertan.
        int hilos = 20;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                Callable<Boolean> envio = () -> {
                    salida.await();
                    try {
                        opiniones.registrar("nav-simultaneo", null, new CrearOpinionRequest(
                                TipoOpinion.COMENTARIO, 1L, "Simultaneo", null, null, null, null, null));
                        return true;
                    } catch (LimiteDeOpinionesExcedido e) {
                        return false;
                    }
                };
                resultados.add(pool.submit(envio));
            }
            salida.countDown();

            int aceptadas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    aceptadas++;
                }
            }
            assertThat(aceptadas).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM opiniones WHERE dispositivo_id = 'nav-simultaneo'", Integer.class))
                .isEqualTo(5);
    }

    private ResultActions opinar(RequestPostProcessor ip, String dispositivo) throws Exception {
        return mockMvc.perform(post("/api/v1/opiniones").with(ip)
                .header("X-Dispositivo-Id", dispositivo)
                .contentType(APPLICATION_JSON).content(OPINION));
    }

    private static RequestPostProcessor desdeIp(String ip) {
        return (MockHttpServletRequest request) -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
