package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-85.
 *
 * Casos adicionales reportados por QA para validar
 * que las entradas invalidas respondan 400
 * utilizando el formato uniforme ApiError.
 */
class HistoricoOperacionEntradasInvalidasIT
        extends IntegracionPostgisTest {

    @Test
    void hora_inicio_mayor_que_23_responde_400_con_api_error()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                                .param("horaInicio", "25")
                                .param("horaFin", "26")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("horaInicio debe estar entre 0 y 23")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/historico/demanda")
                )
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void franja_invertida_responde_400_con_api_error()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "2026-09-17")
                                .param("horaInicio", "10")
                                .param("horaFin", "5")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("horaInicio debe ser menor que horaFin")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/historico/demanda")
                );
    }

    @Test
    void vehiculo_id_cero_responde_400_con_api_error()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/recorrido")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("vehiculoId", "0")
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("vehiculoId debe ser mayor que cero")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/historico/recorrido")
                );
    }

    @Test
    void parada_id_ausente_responde_400_con_api_error()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("fecha", "2026-09-17")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Falta el parametro obligatorio: paradaId")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/historico/demanda")
                );
    }

    @Test
    void fecha_con_formato_invalido_responde_400_con_api_error()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/historico/demanda")
                                .header(
                                        AdminBootstrapFilter.CABECERA,
                                        ADMIN
                                )
                                .param("paradaId", "1")
                                .param("fecha", "fecha-invalida")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("El parametro 'fecha' tiene un formato invalido")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/admin/historico/demanda")
                );
    }
}
