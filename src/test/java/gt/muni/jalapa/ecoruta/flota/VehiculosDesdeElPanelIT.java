package gt.muni.jalapa.ecoruta.flota;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cualquier admin del panel municipal da de alta buses y les asigna ruta y capacidad. */
class VehiculosDesdeElPanelIT extends IntegracionPostgisTest {

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void borrar() {
        jdbc.update("DELETE FROM vehiculos WHERE identificador LIKE 'IT-%'");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crea_un_bus_con_capacidad_y_luego_le_asigna_una_ruta_libre() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/vehiculos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"identificador\":\"IT-01\",\"placa\":\"IT-P01\",\"capacidad\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacidad").value(40))
                .andExpect(jsonPath("$.rutaId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(cuerpo).get("id").asLong();

        // La ruta 1 ya tiene su bus (BUS-01): una ruta, un bus activo.
        mockMvc.perform(put("/api/v1/admin/vehiculos/" + id + "/asignacion")
                        .contentType(APPLICATION_JSON).content("{\"rutaId\":1,\"capacidad\":40}"))
                .andExpect(status().isUnprocessableEntity());

        jdbc.update("UPDATE vehiculos SET ruta_id = NULL WHERE identificador = 'BUS-01'");
        try {
            mockMvc.perform(put("/api/v1/admin/vehiculos/" + id + "/asignacion")
                            .contentType(APPLICATION_JSON).content("{\"rutaId\":1,\"capacidad\":35}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rutaId").value(1))
                    .andExpect(jsonPath("$.capacidad").value(35));
        } finally {
            jdbc.update("DELETE FROM vehiculos WHERE identificador LIKE 'IT-%'");
            jdbc.update("UPDATE vehiculos SET ruta_id = 1 WHERE identificador = 'BUS-01'");
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void una_capacidad_de_cero_no_se_acepta() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vehiculos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"identificador\":\"IT-02\",\"placa\":\"IT-P02\",\"capacidad\":0}"))
                .andExpect(status().isBadRequest());
    }
}
