package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemandaCancelacionIT extends IntegracionPostgisTest {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";

    @Test
    void cancelar_registro_activo_responde_204_y_lo_deja_cancelado()
            throws Exception {

        Long registroId = crearRegistroActivo("dispositivo-uno");

        assertThat(contarActivos()).isEqualTo(1L);

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, "dispositivo-uno")
                )
                .andExpect(status().isNoContent());

        String estado = jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?",
                String.class,
                registroId
        );

        Boolean tieneFechaCancelacion = jdbc.queryForObject(
                """
                SELECT cancelado_en IS NOT NULL
                FROM registros_espera
                WHERE id = ?
                """,
                Boolean.class,
                registroId
        );

        assertThat(estado).isEqualTo("CANCELADA");
        assertThat(tieneFechaCancelacion).isTrue();
        assertThat(contarActivos()).isZero();
    }

    @Test
    void cancelar_registro_inexistente_responde_404()
            throws Exception {

        mockMvc.perform(
                        delete("/api/v1/reservas/999999")
                                .header(CABECERA_DISPOSITIVO, "dispositivo-uno")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void no_permite_cancelar_registro_de_otro_dispositivo()
            throws Exception {

        Long registroId = crearRegistroActivo("dispositivo-dueno");

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, "otro-dispositivo")
                )
                .andExpect(status().isForbidden());

        String estado = jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?",
                String.class,
                registroId
        );

        assertThat(estado).isEqualTo("ACTIVA");
    }

    @Test
    void cancelar_dos_veces_responde_422()
            throws Exception {

        Long registroId = crearRegistroActivo("dispositivo-uno");

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, "dispositivo-uno")
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, "dispositivo-uno")
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void no_permite_cancelar_registro_expirado()
            throws Exception {

        Long registroId = jdbc.queryForObject(
                """
                INSERT INTO registros_espera
                    (dispositivo_id, parada_id, estado, expira_en)
                VALUES
                    (?, 1, 'EXPIRADA', now() - interval '1 minute')
                RETURNING id
                """,
                Long.class,
                "dispositivo-expirado"
        );

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, "dispositivo-expirado")
                )
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void despues_de_cancelar_el_mismo_dispositivo_puede_registrarse_de_nuevo()
            throws Exception {

        String dispositivoId = "dispositivo-reutilizable";

        Long registroId = crearRegistroActivo(dispositivoId);

        mockMvc.perform(
                        delete("/api/v1/reservas/{registroId}", registroId)
                                .header(CABECERA_DISPOSITIVO, dispositivoId)
                )
                .andExpect(status().isNoContent());

        assertThat(contarActivos()).isZero();

        Long nuevoRegistro = crearRegistroActivo(dispositivoId);

        assertThat(nuevoRegistro).isNotNull();
        assertThat(contarActivos()).isEqualTo(1L);
    }

    private Long crearRegistroActivo(String dispositivoId) {

        return jdbc.queryForObject(
                """
                INSERT INTO registros_espera
                    (dispositivo_id, parada_id, estado, expira_en)
                VALUES
                    (?, 1, 'ACTIVA', now() + interval '20 minutes')
                RETURNING id
                """,
                Long.class,
                dispositivoId
        );
    }

    private Long contarActivos() {

        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM registros_espera
                WHERE parada_id = 1
                  AND estado IN ('ACTIVA', 'RENOVADA')
                """,
                Long.class
        );
    }
}