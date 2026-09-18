package gt.muni.jalapa.ecoruta.demanda;

import com.jayway.jsonpath.JsonPath;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.demanda.servicio.ExpiradorDeReservas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        Instant antes = Instant.now();
        String cuerpo = crear("disp-crear", PARADA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.paradaId").value((int) PARADA))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn().getResponse().getContentAsString();

        Instant expiraEn = Instant.parse(JsonPath.read(cuerpo, "$.expiraEn"));

        // Cinco minutos contados desde que se pidio, con holgura. El contrato de
        // SCRUM-306 no expone creadoEn, asi que se mide contra el reloj de la
        // prueba y no contra un campo de la respuesta.
        assertThat(Duration.between(antes, expiraEn))
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
                   AND estado IN ('ACTIVA', 'RENOVADA')
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
    void un_dispositivo_no_puede_renovar_la_reserva_de_otro() throws Exception {
        long id = idDe(crear("disp-dueno", PARADA).andExpect(status().isCreated()));

        mockMvc.perform(post("/api/v1/reservas/" + id + "/renovacion")
                        .header("X-Dispositivo-Id", "disp-intruso"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVA");
    }

    @Test
    void renovar_una_reserva_inexistente_responde_404() throws Exception {
        mockMvc.perform(post("/api/v1/reservas/999999/renovacion")
                        .header("X-Dispositivo-Id", "cualquiera"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void crear_una_reserva_para_una_parada_que_no_existe_responde_404() throws Exception {
        crear("disp-sinparada", 999999L).andExpect(status().isNotFound());
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Reserva por el contrato real de SCRUM-306, que exige coordenadas y aplica
     * la geocerca. Se reserva parado sobre la parada; si la parada no existe,
     * cualquier punto sirve porque el 404 se decide antes que la geocerca.
     */
    private ResultActions crear(String dispositivo, long parada) throws Exception {
        List<Map<String, Object>> puntos = jdbc.queryForList(
                "SELECT ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon FROM paradas WHERE id = ?",
                parada);
        Object lat = puntos.isEmpty() ? 14.6349 : puntos.get(0).get("lat");
        Object lon = puntos.isEmpty() ? -89.9882 : puntos.get(0).get("lon");
        return mockMvc.perform(post("/api/v1/reservas")
                .contentType(APPLICATION_JSON)
                .content("""
                        {"dispositivoId": "%s", "paradaId": %d, "latitud": %s, "longitud": %s}"""
                        .formatted(dispositivo, parada, lat, lon)));
    }

    /** Renueva como el dispositivo que la creo: el unico autorizado a hacerlo. */
    private ResultActions renovar(long id) throws Exception {
        String dueno = jdbc.queryForObject(
                "SELECT dispositivo_id FROM registros_espera WHERE id = ?", String.class, id);
        return mockMvc.perform(post("/api/v1/reservas/" + id + "/renovacion")
                .header("X-Dispositivo-Id", dueno));
    }

    private long idDe(ResultActions respuesta) throws Exception {
        String cuerpo = respuesta.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }
}
