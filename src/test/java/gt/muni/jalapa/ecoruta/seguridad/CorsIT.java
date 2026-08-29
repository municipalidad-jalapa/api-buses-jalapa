package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestPropertySource(properties = {
        "ecoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt,https://buses.jalapa.gob.gt"
})
class CorsIT extends IntegracionPostgisTest {
@Test
void preflight_desde_qa_es_permitido() throws Exception {
    mockMvc.perform(options("/api/v1/telemetria/stream")
                    .header("Origin", "https://qa.buses.jalapa.gob.gt")
                    .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "https://qa.buses.jalapa.gob.gt"))
            .andExpect(header().string(
                    "Access-Control-Allow-Credentials",
                    "true"));
}

@Test
void preflight_desde_produccion_es_permitido() throws Exception {
    mockMvc.perform(options("/api/v1/telemetria/stream")
                    .header("Origin", "https://buses.jalapa.gob.gt")
                    .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "https://buses.jalapa.gob.gt"))
            .andExpect(header().string(
                    "Access-Control-Allow-Credentials",
                    "true"));
}

@Test
void preflight_desde_origen_no_autorizado_es_rechazado() throws Exception {
    mockMvc.perform(options("/api/v1/telemetria/stream")
                    .header("Origin", "https://sitio-no-autorizado.com")
                    .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

}

@Test
void preflight_permite_las_cabeceras_requeridas() throws Exception {
    mockMvc.perform(options("/api/v1/telemetria/stream")
                    .header("Origin", "https://qa.buses.jalapa.gob.gt")
                    .header("Access-Control-Request-Method", "GET")
                    .header(
                            "Access-Control-Request-Headers",
                            "Authorization, Content-Type, Accept, Last-Event-ID"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Headers"))
            .andExpect(header().string(
                    "Access-Control-Max-Age",
                    "3600"));

}

@Test
void stream_expone_las_cabeceras_necesarias_para_el_navegador() throws Exception {
    mockMvc.perform(get("/api/v1/telemetria/stream")
                    .header("Origin", "https://qa.buses.jalapa.gob.gt"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "https://qa.buses.jalapa.gob.gt"))
            .andExpect(header().string(
                    "Access-Control-Expose-Headers",
                    "Cache-Control, Content-Type"))
            .andExpect(header().string(
                    "Cache-Control",
                    "no-cache"));

}

@Test
void preflight_de_endpoint_protegido_no_pide_autenticacion() throws Exception {
    mockMvc.perform(options("/api/v1/telemetria/posiciones")
                    .header("Origin", "https://qa.buses.jalapa.gob.gt")
                    .header("Access-Control-Request-Method", "POST")
                    .header(
                            "Access-Control-Request-Headers",
                            "Authorization, Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "https://qa.buses.jalapa.gob.gt"))
            .andExpect(header().string(
                    "Access-Control-Allow-Credentials",
                    "true"));
   }

}