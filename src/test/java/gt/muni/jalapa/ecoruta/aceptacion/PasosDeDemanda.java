package gt.muni.jalapa.ecoruta.aceptacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pasos de cancelar_reserva.feature.
 * SCRUM-172 / HU-77 (antes SCRUM-276 / HU-124).
 */
public class PasosDeDemanda {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";
    private static final String DISPOSITIVO_PROPIO = "dispositivo-propio";
    private static final long RUTA_ID = 1L;
    private static final long PARADA_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

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

    @Dado("que existe una reserva activa de mi dispositivo")
    public void existe_reserva_activa_de_mi_dispositivo() {
        existe_reserva_activa(DISPOSITIVO_PROPIO);
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

    @Dado("que mi reserva ya esta marcada como abordada")
    public void mi_reserva_ya_esta_marcada_como_abordada() {

        this.dispositivoId = DISPOSITIVO_PROPIO;

        this.registroId = jdbc.queryForObject(
                """
                INSERT INTO registros_espera
                    (dispositivo_id, parada_id, estado, expira_en)
                VALUES
                    (?, 1, 'ABORDO', now() + interval '20 minutes')
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

    @Cuando("cancelo mi reserva")
    public void cancelo_mi_reserva() throws Exception {
        dispositivo_cancela_reserva(dispositivoId);
    }

    @Cuando("intento cancelar mi reserva")
    public void intento_cancelar_mi_reserva() throws Exception {
        dispositivo_cancela_reserva(dispositivoId);
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

    @Y("la reserva queda cancelada con fecha de cancelacion")
    public void reserva_queda_cancelada_con_fecha() {
        reserva_queda_en_estado("CANCELADA");
        reserva_conserva_trazabilidad();
    }

    @Y("la reserva continua marcada como abordada")
    public void reserva_continua_marcada_como_abordada() {
        reserva_queda_en_estado("ABORDO");
    }

    @Y("no se registra una fecha de cancelacion")
    public void no_se_registra_fecha_de_cancelacion() {

        Boolean tieneFecha = jdbc.queryForObject(
                """
                SELECT cancelado_en IS NOT NULL
                FROM registros_espera
                WHERE id = ?
                """,
                Boolean.class,
                registroId
        );

        assertThat(tieneFecha).isFalse();
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

    @Y("el resumen muestra una persona esperando en la parada")
    public void el_resumen_muestra_una_persona_esperando() throws Exception {
        assertThat(conteoEnResumen(PARADA_ID)).isEqualTo(1);
    }

    @Y("el resumen muestra cero personas esperando en la parada")
    public void el_resumen_muestra_cero_personas_esperando() throws Exception {
        assertThat(conteoEnResumen(PARADA_ID)).isZero();
    }

    private int conteoEnResumen(long paradaId) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/rutas/{rutaId}/resumen", RUTA_ID))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode filas = json.readTree(resultado.getResponse().getContentAsString())
                .path("reservasActivas")
                .path("porParada");

        for (JsonNode fila : filas) {
            if (fila.path("paradaId").asLong() == paradaId) {
                return fila.path("reservasActivas").asInt();
            }
        }
        throw new AssertionError("El resumen no incluyo la parada " + paradaId);
    }
}
