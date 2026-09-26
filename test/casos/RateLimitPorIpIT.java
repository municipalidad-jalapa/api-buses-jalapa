package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-95: el limite por IP contra la app real (MockMvc + PostGIS
 * real), para probar que SecurityConfig conecta bien el filtro -- orden en la
 * cadena, formato de la respuesta 429, cabeceras CORS que sobreviven al 429.
 *
 * <p>El limite por dispositivo queda deliberadamente altisimo aqui para que
 * nunca interfiera: lo prueba aparte {@link RateLimitPorDispositivoIT}, con
 * los papeles invertidos. Cada caso usa su propia IP simulada para no heredar
 * el consumo de otro caso: el filtro es un bean unico para toda la clase de
 * prueba, su estado no se reinicia entre metodos @Test.
 */
@TestPropertySource(properties = {
        "ecoruta.rate-limit.por-ip-capacidad=3",
        "ecoruta.rate-limit.por-ip-ventana-segundos=60",
        "ecoruta.rate-limit.por-dispositivo-capacidad=1000",
        "ecoruta.rate-limit.por-dispositivo-ventana-segundos=60",
        "ecoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
})
class RateLimitPorIpIT extends IntegracionPostgisTest {

    @Test
    void supera_el_limite_por_ip_en_una_ruta_publica_y_responde_429() throws Exception {
        RequestPostProcessor ip = desdeIp("10.10.10.1");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/rutas").with(ip)).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/rutas").with(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/v1/rutas"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void otra_ip_no_hereda_el_limite_ya_agotado_de_la_primera() throws Exception {
        RequestPostProcessor agotada = desdeIp("10.10.10.2");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/rutas").with(agotada)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/rutas").with(agotada)).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/rutas").with(desdeIp("10.10.10.3")))
                .andExpect(status().isOk());
    }

    @Test
    void una_ruta_publica_fuera_de_la_lista_protegida_no_se_limita() throws Exception {
        // /actuator/health es publico pero no es de negocio: si se limitara,
        // el healthcheck de docker compose (ADR-009) podria tumbar el contenedor.
        RequestPostProcessor ip = desdeIp("10.10.10.4");
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/actuator/health").with(ip)).andExpect(status().isOk());
        }
    }

    @Test
    void un_429_conserva_las_cabeceras_cors_del_origen_permitido() throws Exception {
        RequestPostProcessor ip = desdeIp("10.10.10.5");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/rutas").with(ip)
                            .header("Origin", "https://qa.buses.jalapa.gob.gt"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/rutas").with(ip)
                        .header("Origin", "https://qa.buses.jalapa.gob.gt"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://qa.buses.jalapa.gob.gt"));
    }

    @Test
    void cancelar_tambien_esta_protegido_por_el_limite_de_ip() throws Exception {
        RequestPostProcessor ip = desdeIp("10.10.10.6");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(delete("/api/v1/reservas/999999")
                            .with(ip)
                            .header("X-Dispositivo-Id", "dispositivo-delete-" + i))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(delete("/api/v1/reservas/999999")
                        .with(ip)
                        .header("X-Dispositivo-Id", "dispositivo-delete-distinto"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void el_login_del_panel_municipal_tambien_esta_protegido_por_el_limite_de_ip() throws Exception {
        // QA (19/09): con la IP agotada, POST /api/v1/auth/admin respondia 400 en
        // vez de 429 porque no estaba en la lista de rutas protegidas. Cada
        // peticion valida un idToken contra Firebase, asi que sin limite permite
        // probar tokens en masa.
        RequestPostProcessor ip = desdeIp("10.10.10.7");

        // Dentro del cupo llega al controlador: cuerpo sin idToken -> 400.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/admin")
                            .with(ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/admin")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/admin"));
    }

    @Test
    void el_cupo_agotado_en_una_ruta_publica_tambien_corta_el_login_del_panel() throws Exception {
        // El escenario exacto de QA: agotar la IP con GET /api/v1/rutas y luego
        // llamar al login de admin desde esa misma IP. La bolsa de IP es una sola
        // para todas las rutas publicas.
        RequestPostProcessor ip = desdeIp("10.10.10.8");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/rutas").with(ip)).andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/admin")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    private static RequestPostProcessor desdeIp(String ip) {
        return (MockHttpServletRequest request) -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
