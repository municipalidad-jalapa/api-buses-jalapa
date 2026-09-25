package gt.muni.jalapa.ecoruta.panel;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasItems;

class CatalogoVehiculosIT extends IntegracionPostgisTest {
    @Test @WithMockUser(roles = "ADMIN")
    void municipalidad_consulta_catalogo_sin_administrar_vehiculos() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogo/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identificador", hasItems("BUS-01", "BUS-02")))
                .andExpect(jsonPath("$[0].id").isNumber());
        mockMvc.perform(get("/api/v1/admin/vehiculos")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/vehiculos")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "CONDUCTOR")
    void piloto_no_consulta_catalogo_municipal() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogo/vehiculos")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "PASAJERO")
    void pasajero_no_consulta_catalogo_municipal() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogo/vehiculos")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "SUPERADMIN")
    void superadmin_consulta_catalogo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogo/vehiculos")).andExpect(status().isOk());
    }
}
