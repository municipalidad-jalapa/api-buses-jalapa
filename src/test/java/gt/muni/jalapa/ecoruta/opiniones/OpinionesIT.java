package gt.muni.jalapa.ecoruta.opiniones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-26 (HU Desarrollo-146), bloque A: opiniones del servicio. */
class OpinionesIT extends IntegracionPostgisTest {

    static final String RUTA = "/api/v1/opiniones";

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmisorDeJwt emisor;

    private long bus1;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM opiniones");
        bus1 = jdbc.queryForObject("SELECT id FROM vehiculos WHERE identificador = 'BUS-01'", Long.class);
    }

    // --- A.1 registro ------------------------------------------------------

    @Test
    void registra_sin_cuenta_y_el_servidor_resuelve_el_vehiculo_de_la_ruta() throws Exception {
        opinar("nav-1", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":4}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rutaId").value(1))
                .andExpect(jsonPath("$.vehiculoId").value(bus1));

        assertThat(jdbc.queryForObject("SELECT dispositivo_id FROM opiniones", String.class)).isEqualTo("nav-1");
    }

    @Test
    void conserva_el_vehiculo_aunque_despues_se_reasigne_el_bus() throws Exception {
        opinar("nav-1", "{\"tipo\":\"comentario\",\"rutaId\":1,\"texto\":\"Todo bien\"}")
                .andExpect(status().isCreated());
        jdbc.update("UPDATE vehiculos SET ruta_id = NULL WHERE id = ?", bus1);
        try {
            assertThat(jdbc.queryForObject("SELECT vehiculo_id FROM opiniones", Long.class)).isEqualTo(bus1);
        } finally {
            jdbc.update("UPDATE vehiculos SET ruta_id = 1 WHERE id = ?", bus1);
        }
    }

    @Test
    void sin_texto_ni_calificacion_responde_422() throws Exception {
        opinar("nav-1", "{\"tipo\":\"comentario\",\"rutaId\":1,\"texto\":\"   \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
        assertThat(filas()).isZero();
    }

    @Test
    void datos_invalidos_responden_422() throws Exception {
        opinar("nav-1", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":6}").andExpect(status().isUnprocessableEntity());
        opinar("nav-1", "{\"tipo\":\"calificacion\",\"rutaId\":999999,\"estrellas\":3}").andExpect(status().isUnprocessableEntity());
        opinar("nav-1", "{\"tipo\":\"felicitacion\",\"rutaId\":1,\"estrellas\":3}").andExpect(status().isUnprocessableEntity());
        opinar("nav-1", "{\"tipo\":\"comentario\",\"rutaId\":1,\"texto\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post(RUTA).contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":3}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void la_reserva_asociada_debe_ser_del_mismo_navegador() throws Exception {
        Long reserva = jdbc.queryForObject("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES ('nav-1', 1, 'ACTIVA', now(), now() + interval '5 minutes') RETURNING id
                """, Long.class);

        opinar("nav-2", "{\"tipo\":\"queja\",\"rutaId\":1,\"texto\":\"No paso\",\"reservaId\":" + reserva + "}")
                .andExpect(status().isUnprocessableEntity());
        opinar("nav-1", "{\"tipo\":\"queja\",\"rutaId\":1,\"texto\":\"No paso\",\"reservaId\":" + reserva + "}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT reserva_id FROM opiniones", Long.class)).isEqualTo(reserva);
    }

    @Test
    void al_exceder_el_limite_de_envios_responde_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            opinar("nav-limite", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":5}").andExpect(status().isCreated());
        }
        opinar("nav-limite", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":5}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
        // Otro navegador no se ve afectado.
        opinar("nav-otro", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":5}").andExpect(status().isCreated());
    }

    @Test
    void el_texto_se_guarda_tal_cual_y_se_devuelve_neutralizado() throws Exception {
        String malicioso = "<script>alert('x')</script> & gracias";
        opinar("nav-1", json.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                "tipo", "comentario", "rutaId", 1, "texto", malicioso)))).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT texto FROM opiniones", String.class)).isEqualTo(malicioso);
        JsonNode lista = listar("");
        assertThat(lista.at("/opiniones/0/texto").asText())
                .isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; gracias");
    }

    @Test
    void conserva_espacios_y_entidades_en_el_round_trip() throws Exception {
        for (String texto : java.util.List.of("  <script>alert(\"x\")</script> & texto  ",
                "  &lt;b&gt; &amp; &#39; 😀\n  ", "   ")) {
            jdbc.update("DELETE FROM opiniones");
            opinar("literal", json.writeValueAsString(java.util.Map.of(
                    "tipo", "comentario", "rutaId", 1, "texto", texto, "estrellas", 4)))
                    .andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("SELECT texto FROM opiniones", String.class)).isEqualTo(texto);
            String devuelto = listar("").at("/opiniones/0/texto").asText();
            assertThat(devuelto).isEqualTo(org.springframework.web.util.HtmlUtils.htmlEscape(texto));
            assertThat(org.springframework.web.util.HtmlUtils.htmlUnescape(devuelto)).isEqualTo(texto);
        }
    }

    // --- A.3 panel municipal -------------------------------------------------

    @Test
    void el_panel_lista_de_la_mas_reciente_a_la_mas_antigua_con_filtros_y_paginacion() throws Exception {
        insertar("QUEJA", 1, 2, "vieja", Instant.now().minus(Duration.ofDays(3)));
        insertar("CALIFICACION", 1, 4, null, Instant.now().minus(Duration.ofDays(1)));
        insertar("COMENTARIO", 2, null, "otra ruta", Instant.now());

        JsonNode todas = listar("");
        assertThat(todas.get("total").asLong()).isEqualTo(3);
        assertThat(todas.at("/opiniones/0/texto").asText()).isEqualTo("otra ruta");
        assertThat(todas.at("/opiniones/2/texto").asText()).isEqualTo("vieja");
        assertThat(todas.at("/opiniones/0/ruta").asText()).isNotBlank();

        assertThat(listar("?tipo=queja").get("total").asLong()).isEqualTo(1);
        assertThat(listar("?rutaId=1").get("total").asLong()).isEqualTo(2);
        assertThat(listar("?vehiculoId=" + bus1).get("total").asLong()).isEqualTo(2);
        assertThat(listar("?desde=" + Instant.now().minus(Duration.ofDays(2))).get("total").asLong()).isEqualTo(2);

        JsonNode pagina = listar("?tamano=2&pagina=1");
        assertThat(pagina.get("opiniones").size()).isEqualTo(1);
        assertThat(pagina.get("total").asLong()).isEqualTo(3);
    }

    @Test
    void el_resumen_trae_el_promedio_por_ruta_y_por_vehiculo() throws Exception {
        insertar("CALIFICACION", 1, 5, null, Instant.now());
        insertar("CALIFICACION", 1, 3, null, Instant.now());
        insertar("COMENTARIO", 1, null, "sin estrellas", Instant.now());

        JsonNode resumen = listar("?rutaId=1").get("resumen");
        assertThat(resumen.get("total").asLong()).isEqualTo(3);
        JsonNode porRuta = resumen.get("promedioPorRuta").get(0);
        assertThat(porRuta.get("promedio").asDouble()).isEqualTo(4.0);
        assertThat(porRuta.get("calificadas").asLong()).isEqualTo(2);
        assertThat(porRuta.get("opiniones").asLong()).isEqualTo(3);
        assertThat(resumen.at("/promedioPorVehiculo/0/nombre").asText()).isEqualTo("BUS-01");
    }

    @Test
    void marcar_como_atendida_registra_quien_y_cuando_y_no_se_reescribe() throws Exception {
        jdbc.update("INSERT INTO usuarios (username, rol, activo, firebase_uid) VALUES ('jefa-it', 'ADMIN', TRUE, 'uid-jefa-it')");
        try {
            long id = insertar("QUEJA", 1, null, "Tarde", Instant.now());
            String token = emisor.emitir("uid-jefa-it", EmisorDeJwt.ROL_ADMIN, Duration.ofMinutes(30)).token();

            mockMvc.perform(patch(RUTA + "/" + id + "/atendida").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.atendidaPor").value("jefa-it"))
                    .andExpect(jsonPath("$.atendidaEn").exists());
            String primera = jdbc.queryForObject("SELECT atendida_en::text FROM opiniones WHERE id = ?", String.class, id);

            mockMvc.perform(patch(RUTA + "/" + id + "/atendida").header("X-Admin-Token", ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.atendidaPor").value("jefa-it"));
            assertThat(jdbc.queryForObject("SELECT atendida_en::text FROM opiniones WHERE id = ?", String.class, id))
                    .isEqualTo(primera);

            mockMvc.perform(patch(RUTA + "/999999/atendida").header("X-Admin-Token", ADMIN))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM usuarios WHERE username = 'jefa-it'");
        }
    }

    @Test
    void solo_el_administrador_lista_o_atiende() throws Exception {
        long id = insertar("QUEJA", 1, null, "x", Instant.now());
        String conductor = emisor.emitirParaConductor("uid-conductor").token();

        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + conductor))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(RUTA + "/" + id + "/atendida").header(HttpHeaders.AUTHORIZATION, "Bearer " + conductor))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(RUTA + "/" + id + "/atendida")).andExpect(status().isUnauthorized());
    }

    private ResultActions opinar(String dispositivo, String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA).header("X-Dispositivo-Id", dispositivo)
                .contentType(APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode listar(String consulta) throws Exception {
        return json.readTree(mockMvc.perform(get(RUTA + consulta).header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long insertar(String tipo, long rutaId, Integer estrellas, String texto, Instant creada) {
        return jdbc.queryForObject("""
                INSERT INTO opiniones (tipo, ruta_id, vehiculo_id, dispositivo_id, texto, estrellas, creada_en)
                VALUES (?, ?, (SELECT id FROM vehiculos WHERE ruta_id = ? AND activo), 'nav-it', ?, ?, ?)
                RETURNING id
                """, Long.class, tipo, rutaId, rutaId, texto, estrellas, java.sql.Timestamp.from(creada));
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM opiniones", Integer.class);
    }
}
