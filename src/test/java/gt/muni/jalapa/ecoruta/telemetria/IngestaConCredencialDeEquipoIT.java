package gt.muni.jalapa.ecoruta.telemetria;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-142, criterio (b): "la ingesta deja de exigir rol CONDUCTOR y acepta
 * credencial de dispositivo".
 *
 * <p>Como la ingesta se construye de cero en esta rama, no hay ningun chequeo de
 * CONDUCTOR que borrar: lo revisable es que exige ROLE_EQUIPO y que ese rol se
 * obtiene solo con la credencial del equipo.
 */
class IngestaConCredencialDeEquipoIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    private Long busPiloto() {
        return vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
    }

    private AltaDeEquipo emitir() {
        return equipoService.emitir(busPiloto(), "Tableta de pruebas");
    }

    private static String bearer(AltaDeEquipo alta) {
        return "Bearer " + alta.credencial().credencialCompleta();
    }

    @Test
    void con_la_credencial_del_equipo_la_ingesta_se_acepta_con_202() throws Exception {
        AltaDeEquipo equipo = emitir();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6335, -89.9885, Instant.now())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(1))
                .andExpect(jsonPath("$.descartadas").value(0));

        assertThat(jdbc.queryForObject(
                "SELECT equipo_id FROM posiciones_historicas", Long.class))
                .isEqualTo(equipo.equipoId());
        assertThat(jdbc.queryForObject(
                "SELECT vehiculo_id FROM posiciones_historicas", Long.class))
                .isEqualTo(busPiloto());
    }

    @Test
    void sin_credencial_la_ingesta_se_rechaza_con_el_formato_ApiError() throws Exception {
        // Caso 3.1 de la coleccion de Postman.
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.633, -89.989, Instant.now())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/telemetria/posiciones"));

        assertThat(cuantasPosiciones()).isZero();
    }

    @Test
    void un_token_basura_se_rechaza_con_401_y_no_con_500() throws Exception {
        // Caso 3.7 de Postman: el filtro no puede reventar con una cabecera rara.
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto.no.es.un.jwt")
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.633, -89.989, Instant.now())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_codigo_valido_con_secreto_equivocado_se_rechaza() throws Exception {
        AltaDeEquipo equipo = emitir();
        String falsificada = equipo.credencial().codigoPublico() + "."
                + "0123456789012345678901234567890123456789012";

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + falsificada)
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.633, -89.989, Instant.now())))
                .andExpect(status().isUnauthorized());

        assertThat(cuantasPosiciones()).isZero();
    }

    @Test
    void el_secreto_en_la_url_no_sirve_para_autenticarse() throws Exception {
        // Criterio (d): el secreto solo se acepta en la cabecera. Si la URL
        // funcionara, acabaria en los access logs del proxy inverso.
        AltaDeEquipo equipo = emitir();

        String cuerpo = mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .param("token", equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.633, -89.989, Instant.now())))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain(equipo.credencial().secreto());
        assertThat(cuantasPosiciones()).isZero();
    }

    @Test
    void una_credencial_revocada_deja_de_ser_aceptada_de_inmediato() throws Exception {
        // Criterio (c), extremo a extremo y en un solo metodo: sin reiniciar el
        // contexto, sin esperar expiracion, sin invalidar cache alguna.
        AltaDeEquipo equipo = emitir();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6335, -89.9885, Instant.now())))
                .andExpect(status().isAccepted());
        assertThat(cuantasPosiciones()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/equipos/" + equipo.equipoId() + "/revocacion")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6400, -89.9900, Instant.now())))
                .andExpect(status().isUnauthorized());

        assertThat(cuantasPosiciones()).isEqualTo(1);
    }

    @Test
    void revocar_un_equipo_no_corta_a_los_demas() throws Exception {
        // "poder revocar un solo equipo" del enunciado de la historia.
        AltaDeEquipo uno = emitir();
        Long otroBus = vehiculos.save(new gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo(
                "BUS-96", "P-966XXX")).getId();
        AltaDeEquipo otro = equipoService.emitir(otroBus, "Tableta 2");

        mockMvc.perform(post("/api/v1/admin/equipos/" + uno.equipoId() + "/revocacion")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(uno))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.63, -89.98, Instant.now())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otro))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.64, -89.99, Instant.now())))
                .andExpect(status().isAccepted());
    }

    @Test
    void la_posicion_vigente_es_la_del_timestamp_mas_reciente_y_no_la_ultima_del_array()
            throws Exception {
        // Casos 3.6a y 3.6b de Postman: la mas nueva va EN MEDIO del lote.
        AltaDeEquipo equipo = emitir();
        Instant ahora = Instant.now();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": 14.6301, "longitud": -89.9801, "velocidadKmh": 10, "timestamp": "%s"},
                                  {"latitud": 14.6399, "longitud": -89.9899, "velocidadKmh": 30, "timestamp": "%s"},
                                  {"latitud": 14.6300, "longitud": -89.9800, "velocidadKmh": 5,  "timestamp": "%s"}
                                ]}""".formatted(
                                ahora.minus(Duration.ofMinutes(1)),
                                ahora,
                                ahora.minus(Duration.ofMinutes(3)))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(3));

        mockMvc.perform(get("/api/v1/telemetria/posicion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitud").value(14.6399))
                .andExpect(jsonPath("$.longitud").value(-89.9899));
    }

    @Test
    void un_reloj_desfasado_se_descarta_pero_el_lote_sigue_dando_202() throws Exception {
        // Casos 3.6c y 3.6d de Postman.
        AltaDeEquipo equipo = emitir();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6335, -89.9885, Instant.now())))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.5000, -89.5000, Instant.now().minus(Duration.ofDays(2)))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(0))
                .andExpect(jsonPath("$.descartadas").value(1));

        assertThat(cuantasPosiciones()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/telemetria/posicion"))
                .andExpect(jsonPath("$.latitud").value(14.6335));
    }

    @Test
    void la_posicion_vigente_responde_204_antes_de_la_primera_ingesta() throws Exception {
        mockMvc.perform(get("/api/v1/telemetria/posicion"))
                .andExpect(status().isNoContent());
    }

    @Test
    void la_posicion_vigente_es_publica_y_no_pide_credencial() throws Exception {
        AltaDeEquipo equipo = emitir();
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6335, -89.9885, Instant.now())))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/telemetria/posicion"))
                .andExpect(status().isOk());
    }

    @Test
    void las_coordenadas_se_guardan_en_el_orden_correcto() throws Exception {
        // ADR-007 avisa del error clasico: PostGIS y JTS son (lon, lat) mientras el
        // DTO se llama latitud/longitud. Se comprueba en SQL crudo y no solo en el
        // DTO, porque a nivel de DTO pasaria igual si escritura y lectura
        // invirtieran de forma consistente.
        AltaDeEquipo equipo = emitir();
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6335, -89.9885, Instant.now())))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject(
                "SELECT ST_Y(ubicacion) FROM posiciones_historicas", Double.class))
                .isEqualTo(14.6335);
        assertThat(jdbc.queryForObject(
                "SELECT ST_X(ubicacion) FROM posiciones_historicas", Double.class))
                .isEqualTo(-89.9885);
        assertThat(jdbc.queryForObject(
                "SELECT ST_SRID(ubicacion) FROM posiciones_historicas", Integer.class))
                .isEqualTo(4326);
    }

    @Test
    void falta_la_latitud_y_responde_400_nombrando_el_campo() throws Exception {
        AltaDeEquipo equipo = emitir();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"longitud": -89.9885, "velocidadKmh": 18, "timestamp": "2026-08-17T10:00:00Z"}
                                ]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("latitud")));
    }

    @Test
    void un_timestamp_ilegible_responde_400_sin_devolver_el_cuerpo_recibido() throws Exception {
        AltaDeEquipo equipo = emitir();

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(equipo))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"posiciones": [
                                  {"latitud": 14.63, "longitud": -89.98, "timestamp": "ayer por la tarde"}
                                ]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Cuerpo de la peticion malformado o ilegible"));
    }

    private long cuantasPosiciones() {
        return jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Long.class);
    }

    private static String lote(double latitud, double longitud, Instant cuando) {
        return """
                {"posiciones": [
                  {"latitud": %s, "longitud": %s, "velocidadKmh": 18, "timestamp": "%s"}
                ]}""".formatted(latitud, longitud, cuando);
    }
}
