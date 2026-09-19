package gt.muni.jalapa.ecoruta.aceptacion;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pasos de cancelar_reserva.feature.
 * SCRUM-276 / HU-124.
 */
public class PasosDeDemanda {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    private Long registroId;
    private String dispositivoId;

    @Dado("que existe una reserva activa del dispositivo {string}")
    public void existe_reserva_activa(String dispositivoId) {

        this.dispositivoId = dispositivoId;

        this.registroId = jdbc.queryForObject(
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

    @Dado("que existe una reserva expirada del dispositivo {string}")
    public void existe_reserva_expirada(String dispositivoId) {

        this.dispositivoId = dispositivoId;

        this.registroId = jdbc.queryForObject(
                """
                INSERT INTO registros_espera
                    (dispositivo_id, parada_id, estado, expira_en)
                VALUES
                    (?, 1, 'EXPIRADA', now() - interval '1 minute')
                RETURNING id
                """,
                Long.class,
                dispositivoId
        );
    }

    @Cuando("el dispositivo {string} cancela su reserva")
    public void dispositivo_cancela_reserva(String dispositivoId)
            throws Exception {

        contexto.guardarRespuesta(
                mockMvc.perform(
                        delete(
                                "/api/v1/reservas/{registroId}",
                                registroId
                        )
                                .header(
                                        CABECERA_DISPOSITIVO,
                                        dispositivoId
                                )
                )
        );
    }

    @Cuando("el dispositivo {string} intenta cancelar la reserva inexistente {long}")
    public void cancelar_reserva_inexistente(
            String dispositivoId,
            Long id
    ) throws Exception {

        contexto.guardarRespuesta(
                mockMvc.perform(
                        delete(
                                "/api/v1/reservas/{registroId}",
                                id
                        )
                                .header(
                                        CABECERA_DISPOSITIVO,
                                        dispositivoId
                                )
                )
        );
    }

    @Cuando("otro dispositivo {string} intenta cancelar la reserva")
    public void otro_dispositivo_intenta_cancelar(String dispositivoId)
            throws Exception {

        contexto.guardarRespuesta(
                mockMvc.perform(
                        delete(
                                "/api/v1/reservas/{registroId}",
                                registroId
                        )
                                .header(
                                        CABECERA_DISPOSITIVO,
                                        dispositivoId
                                )
                )
        );
    }

    @Cuando("el dispositivo {string} vuelve a cancelar la misma reserva")
    public void vuelve_a_cancelar(
            String dispositivoId
    ) throws Exception {

        contexto.guardarRespuesta(
                mockMvc.perform(
                        delete(
                                "/api/v1/reservas/{registroId}",
                                registroId
                        )
                                .header(
                                        CABECERA_DISPOSITIVO,
                                        dispositivoId
                                )
                )
        );
    }

    @Cuando("el mismo dispositivo crea otra reserva activa")
    public void mismo_dispositivo_crea_otra_reserva() {

        int filas = jdbc.update(
                """
                INSERT INTO registros_espera
                    (dispositivo_id, parada_id, estado, expira_en)
                VALUES
                    (?, 1, 'ACTIVA', now() + interval '20 minutes')
                """,
                dispositivoId
        );

        assertThat(filas).isEqualTo(1);
    }

    @Entonces("la respuesta tiene codigo {int}")
    public void respuesta_tiene_codigo(int codigo)
            throws Exception {

        contexto.ultimaRespuesta()
                .andExpect(status().is(codigo));
    }

    @Entonces("la reserva queda en estado {string}")
    public void reserva_queda_en_estado(String estado) {

        String estadoActual = jdbc.queryForObject(
                """
                SELECT estado
                FROM registros_espera
                WHERE id = ?
                """,
                String.class,
                registroId
        );

        assertThat(estadoActual).isEqualTo(estado);
    }

    @Y("la reserva sigue almacenada y tiene fecha de cancelacion")
    public void reserva_conserva_trazabilidad() {

        Integer registros = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM registros_espera
                WHERE id = ?
                  AND cancelado_en IS NOT NULL
                """,
                Integer.class,
                registroId
        );

        assertThat(registros).isEqualTo(1);
    }

    @Entonces("el conteo de reservas activas en la parada es {int}")
    public void conteo_de_reservas_activas(int esperado) {

        Integer cantidad = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM registros_espera
                WHERE parada_id = 1
                  AND estado IN ('ACTIVA', 'RENOVADA')
                """,
                Integer.class
        );

        assertThat(cantidad).isEqualTo(esperado);
    }
}