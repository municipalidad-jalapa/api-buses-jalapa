package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-95: el limite por dispositivo ({@code X-Dispositivo-Id})
 * contra la app real. El limite por IP queda deliberadamente altisimo aqui
 * para que nunca interfiera -- lo prueba aparte {@link RateLimitPorIpIT}. En
 * MockMvc todas las peticiones comparten la misma IP simulada por defecto, lo
 * que de hecho ayuda a probar que el limite por dispositivo es independiente
 * del de IP.
 */
@TestPropertySource(properties = {
        "ecoruta.rate-limit.por-ip-capacidad=1000",
        "ecoruta.rate-limit.por-ip-ventana-segundos=60",
        "ecoruta.rate-limit.por-dispositivo-capacidad=2",
        "ecoruta.rate-limit.por-dispositivo-ventana-segundos=60"
})
class RateLimitPorDispositivoIT extends IntegracionPostgisTest {

    private static final String RUTA = "/api/v1/reservas/999999/renovacion";

    @Test
    void supera_el_limite_por_dispositivo_y_responde_429() throws Exception {
        String dispositivo = "dispositivo-ritmo-alto";

        // La ruta no existe (404), pero eso solo se sabe si la peticion llega
        // al controlador: si el filtro corta antes, nunca aparece un 404.
        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", dispositivo))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", dispositivo))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", dispositivo))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void otro_dispositivo_no_hereda_el_limite_ya_agotado_del_primero() throws Exception {
        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", "dispositivo-agotado"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", "dispositivo-agotado"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", "dispositivo-agotado"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", "dispositivo-nuevo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sin_cabecera_de_dispositivo_el_limite_por_dispositivo_no_aplica() throws Exception {
        // GET /api/v1/rutas nunca trae X-Dispositivo-Id. Con el limite de IP
        // en 1000 para esta clase, cinco peticiones deben pasar todas.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/rutas")).andExpect(status().isOk());
        }
    }
}
