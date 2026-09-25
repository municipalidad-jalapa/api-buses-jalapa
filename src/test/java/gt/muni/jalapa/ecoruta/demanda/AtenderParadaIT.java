package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AtenderParadaIT extends IntegracionPostgisTest {

    private static final String RUTA =
            "/api/v1/rutas/1/paradas/1/atendida";

  @BeforeEach
void limpiarDatosHu76() {

    jdbc.update("""
            DELETE FROM paradas_atendidas
            """);

    jdbc.update("""
            DELETE FROM registros_espera
            """);
}

    @Test
    @WithMockUser(
            username = "conductor1",
            roles = "CONDUCTOR"
    )
    void conductor_marca_parada_y_cierra_reservas()
            throws Exception {

        insertarReserva("ACTIVA");
        insertarReserva("RENOVADA");

        mockMvc.perform(post(RUTA))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.reservasCerradas")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.marcadaEn")
                                .isNotEmpty()
                );

        Long abordadas = jdbc.queryForObject("""
                SELECT count(*)
                  FROM registros_espera
                 WHERE parada_id = 1
                   AND estado = 'ABORDO'
                """,
                Long.class);

        assertThat(abordadas).isEqualTo(2);

        Long conAuditoria = jdbc.queryForObject("""
                SELECT count(*)
                  FROM registros_espera
                 WHERE parada_id = 1
                   AND estado = 'ABORDO'
                   AND abordaje_en IS NOT NULL
                   AND abordado_por = 'conductor1'
                """,
                Long.class);

        assertThat(conAuditoria).isEqualTo(2);
    }

    @Test
    @WithMockUser(
            username = "conductor1",
            roles = "CONDUCTOR"
    )
    void marcar_misma_parada_dos_veces_responde_409()
            throws Exception {

        insertarReserva("ACTIVA");

        mockMvc.perform(post(RUTA))
                .andExpect(status().isOk());

        mockMvc.perform(post(RUTA))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("La parada ya fue marcada como atendida.")
                );
    }

    @Test
    @WithMockUser(
            username = "conductor1",
            roles = "CONDUCTOR"
    )
    void guarda_cuantos_subieron_y_bajaron_contando_a_los_que_no_avisaron()
            throws Exception {

        insertarReserva("ACTIVA");

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subieron\":3,\"bajaron\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasCerradas").value(1));

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT subieron, bajaron FROM paradas_atendidas WHERE parada_id = 1");

        assertThat(fila).containsEntry("subieron", 3).containsEntry("bajaron", 2);
    }

    @Test
    @WithMockUser(
            username = "conductor1",
            roles = "CONDUCTOR"
    )
    void un_conteo_negativo_responde_400_y_no_marca_la_parada()
            throws Exception {

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subieron\":-1,\"bajaron\":0}"))
                .andExpect(status().isBadRequest());

        Long marcadas = jdbc.queryForObject("SELECT count(*) FROM paradas_atendidas", Long.class);
        assertThat(marcadas).isZero();
    }

    @Test
    void sin_autenticacion_responde_401()
            throws Exception {

        mockMvc.perform(post(RUTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(
            username = "otro-conductor",
            roles = "CONDUCTOR"
    )
    void conductor_sin_ruta_asignada_responde_403()
            throws Exception {

        mockMvc.perform(post(RUTA))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );
    }

    private void insertarReserva(String estado) {

        jdbc.update("""
                INSERT INTO registros_espera (
                    dispositivo_id,
                    parada_id,
                    estado,
                    creado_en,
                    expira_en
                )
                VALUES (
                    ?,
                    1,
                    ?,
                    now(),
                    now() + interval '20 minutes'
                )
                """,
                UUID.randomUUID().toString(),
                estado);
    }
}