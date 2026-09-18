package gt.muni.jalapa.ecoruta.demanda;

import com.jayway.jsonpath.JsonPath;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.demanda.servicio.ExpiradorDeReservas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desarrollo-135 (HU): vigencia de cinco minutos con renovacion y expiracion.
 *
 * <p>Un caso por criterio de aceptacion. El tiempo se mueve tocando
 * {@code expira_en} en la base, que es el patron del resto de la suite.
 */
class ReservaVigenciaIT extends IntegracionPostgisTest {

    /** Parada que siembra V6. */
    private static final long PARADA = 1L;

    @Autowired
    private ExpiradorDeReservas expirador;

    @Test
    void al_crearse_la_reserva_expira_cinco_minutos_despues() throws Exception {
        String cuerpo = crear("disp-crear", PARADA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.paradaId").value((int) PARADA))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn().getResponse().getContentAsString();

        Instant creadoEn = Instant.parse(JsonPath.read(cuerpo, "$.creadoEn"));
        Instant expiraEn = Instant.parse(JsonPath.read(cuerpo, "$.expiraEn"));

        // Cinco minutos, con holgura por el desfase entre el now() de la base y
        // el Instant.now() del servicio.
        assertThat(Duration.between(creadoEn, expiraEn))
                .isBetween(Duration.ofMinutes(4), Duration.ofMinutes(6));
    }

    @Test
    void renovar_una_reserva_vigente_extiende_la_expiracion_y_responde_200() throws Exception {
        long id = idDe(crear("disp-renueva", PARADA).andExpect(status().isCreated()));

        // La dejamos a punto de vencer pero todavia vigente.
        jdbc.update("UPDATE registros_espera SET expira_en = now() + interval '10 seconds' WHERE id = ?", id);

        String cuerpo = renovar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.estado").value("RENOVADA"))
                .andReturn().getResponse().getContentAsString();

        Instant nuevoExpiraEn = Instant.parse(JsonPath.read(cuerpo, "$.expiraEn"));
        // El nuevo vencimiento esta ~5 min por delante, muy por encima de los 10 s
        // a los que lo habiamos dejado.
        assertThat(nuevoExpiraEn).isAfter(Instant.now().plus(Duration.ofMinutes(4)));
        assertThat(nuevoExpiraEn).isBefore(Instant.now().plus(Duration.ofMinutes(6)));
    }

    @Test
    void renovar_una_reserva_ya_expirada_responde_422() throws Exception {
        long id = idDe(crear("disp-expirada", PARADA).andExpect(status().isCreated()));
        jdbc.update("UPDATE registros_espera SET expira_en = now() - interval '1 minute' WHERE id = ?", id);

        renovar(id)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/reservas/" + id + "/renovacion"));
    }

    @Test
    void renovar_una_reserva_que_no_esta_activa_responde_422() throws Exception {
        long id = idDe(crear("disp-cancelada", PARADA).andExpect(status().isCreated()));
        // Vigente por fecha, pero en un estado desde el que no se renueva.
        jdbc.update("UPDATE registros_espera SET estado = 'CANCELADA' WHERE id = ?", id);

        renovar(id)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void la_tarea_programada_marca_expirada_toda_reserva_vencida() throws Exception {
        long id = idDe(crear("disp-barrido", PARADA).andExpect(status().isCreated()));
        jdbc.update("UPDATE registros_espera SET expira_en = now() - interval '1 second' WHERE id = ?", id);

        expirador.barrer();

        assertThat(jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?", String.class, id))
                .isEqualTo("EXPIRADA");
    }

    @Test
    void una_reserva_expirada_no_cuenta_como_activa_ni_impide_una_nueva() throws Exception {
        long primera = idDe(crear("disp-repite", PARADA).andExpect(status().isCreated()));
        jdbc.update("UPDATE registros_espera SET expira_en = now() - interval '1 minute' WHERE id = ?", primera);
        expirador.barrer();

        // El mismo dispositivo crea otra sin tropezar con el indice unico parcial.
        crear("disp-repite", PARADA).andExpect(status().isCreated());

        Integer vigentes = jdbc.queryForObject("""
                SELECT count(*) FROM registros_espera
                 WHERE dispositivo_id = 'disp-repite'
                   AND estado IN ('ACTIVA', 'RENOVADA', 'ABORDO')
                """, Integer.class);
        assertThat(vigentes).isEqualTo(1);

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM registros_espera WHERE dispositivo_id = 'disp-repite'",
                Integer.class);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void un_dispositivo_con_reserva_vigente_no_puede_crear_otra() throws Exception {
        crear("disp-doble", PARADA).andExpect(status().isCreated());
        crear("disp-doble", PARADA).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void renovar_una_reserva_inexistente_responde_404() throws Exception {
        mockMvc.perform(post("/api/v1/reservas/999999/renovacion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void crear_una_reserva_para_una_parada_que_no_existe_responde_404() throws Exception {
        crear("disp-sinparada", 999999L).andExpect(status().isNotFound());
    }

    // --- helpers ---------------------------------------------------------------

    private ResultActions crear(String dispositivo, long parada) throws Exception {
        return mockMvc.perform(post("/api/v1/reservas")
                .contentType(APPLICATION_JSON)
                .content("""
                        {"dispositivoId": "%s", "paradaId": %d}""".formatted(dispositivo, parada)));
    }

    private ResultActions renovar(long id) throws Exception {
        return mockMvc.perform(post("/api/v1/reservas/" + id + "/renovacion"));
    }

    private long idDe(ResultActions respuesta) throws Exception {
        String cuerpo = respuesta.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }
}
