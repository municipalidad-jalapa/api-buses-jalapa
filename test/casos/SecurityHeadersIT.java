package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU Desarrollo-95, criterio "cabeceras de seguridad configuradas y
 * verificadas". Contra la app real (MockMvc + PostGIS real): lo que importa
 * es lo que el navegador recibe de verdad, no solo que SecurityConfig
 * compile.
 */
class SecurityHeadersIT extends IntegracionPostgisTest {

    @Test
    void una_respuesta_publica_trae_las_cabeceras_de_seguridad() throws Exception {
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "geolocation=(), camera=(), microphone=(), payment=(), usb=()"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }

    @Test
    void la_csp_no_se_aplica_fuera_de_la_api_para_no_romper_swagger_ui() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                // El resto de cabeceras si son globales: no dependen de la ruta.
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void hsts_solo_viaja_en_una_conexion_que_el_servidor_considera_segura() throws Exception {
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mockMvc.perform(get("/api/v1/rutas").secure(true))
                .andExpect(header().string(
                        "Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void un_error_de_negocio_tambien_trae_las_cabeceras_de_seguridad() throws Exception {
        // Las cabeceras las pone el filtro de Spring Security, antes de que el
        // request llegue al controlador: un 404 tiene que traerlas igual que un 200.
        mockMvc.perform(get("/api/v1/rutas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }
}
