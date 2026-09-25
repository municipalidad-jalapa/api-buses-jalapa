package gt.muni.jalapa.ecoruta.panel;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-26 (HU Desarrollo-146), bloque F: lo que el panel municipal muestra a
 * partir de los abordajes del piloto y de las tres valoraciones del pasajero.
 */
class MetricasDelPanelIT extends IntegracionPostgisTest {

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM opiniones");
    }

    // --- criterio 1: pasajeros subidos ---------------------------------------

    @Test
    void cuenta_los_abordajes_marcados_por_el_piloto_por_ruta_vehiculo_y_periodo() throws Exception {
        abordajeDelPiloto(1L, "2026-09-18T14:00:00Z");
        abordajeDelPiloto(1L, "2026-09-18T15:00:00Z");
        abordajeDelPiloto(1L, "2026-09-19T09:00:00Z");
        abordajeDelPiloto(2L, "2026-09-19T10:00:00Z");

        mockMvc.perform(get("/api/v1/admin/abordajes").header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.granularidad").value("DIA"))
                .andExpect(jsonPath("$.porRuta.length()").value(2))
                .andExpect(jsonPath("$.porVehiculo.length()").value(2))
                // 18 y 19 de septiembre.
                .andExpect(jsonPath("$.porPeriodo.length()").value(2));
    }

    @Test
    void lo_que_dijo_el_pasajero_no_cuenta_si_el_piloto_no_lo_marco() throws Exception {
        abordajeDelPasajero(1L, "2026-09-18T14:00:00Z");
        abordajeDelPiloto(1L, "2026-09-18T15:00:00Z");

        mockMvc.perform(get("/api/v1/admin/abordajes").header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void el_periodo_y_la_ruta_filtran_el_conteo() throws Exception {
        abordajeDelPiloto(1L, "2026-09-18T14:00:00Z");
        abordajeDelPiloto(1L, "2026-09-19T09:00:00Z");
        abordajeDelPiloto(2L, "2026-09-19T10:00:00Z");

        mockMvc.perform(get("/api/v1/admin/abordajes")
                        .param("desde", "2026-09-19T00:00:00Z")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/admin/abordajes")
                        .param("rutaId", "1")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.porRuta.length()").value(1));

        mockMvc.perform(get("/api/v1/admin/abordajes")
                        .param("granularidad", "mes")
                        .header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.granularidad").value("MES"))
                .andExpect(jsonPath("$.porPeriodo.length()").value(1));
    }

    @Test
    void el_conteo_de_abordajes_es_del_panel_y_no_es_publico() throws Exception {
        mockMvc.perform(get("/api/v1/admin/abordajes")).andExpect(status().isUnauthorized());
    }

    // --- criterios 2 y 3: las tres valoraciones ------------------------------

    @Test
    void la_opinion_acepta_las_tres_valoraciones_como_campos_opcionales() throws Exception {
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-f-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"tipo":"calificacion","rutaId":1,"calidad":4,"limpieza":2,"conduccion":5}"""))
                .andExpect(status().isCreated());

        // Sigue valiendo la calificacion general sola, del bloque A.
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-f-2")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"estrellas\":5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/opiniones").header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opiniones[1].calidad").value(4))
                .andExpect(jsonPath("$.opiniones[1].limpieza").value(2))
                .andExpect(jsonPath("$.opiniones[1].conduccion").value(5));
    }

    @Test
    void cada_dimension_se_promedia_por_separado_por_ruta_y_por_vehiculo() throws Exception {
        opinar("nav-f-3", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"calidad\":4,\"limpieza\":2,\"conduccion\":5}");
        opinar("nav-f-4", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"calidad\":5,\"limpieza\":3,\"conduccion\":4}");

        mockMvc.perform(get("/api/v1/opiniones").header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].calidad").value(4.5))
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].limpieza").value(2.5))
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].conduccion").value(4.5))
                .andExpect(jsonPath("$.resumen.promedioPorVehiculo[0].limpieza").value(2.5));
    }

    @Test
    void una_dimension_que_nadie_puntuo_sale_sin_dato_y_no_como_cero() throws Exception {
        opinar("nav-f-5", "{\"tipo\":\"calificacion\",\"rutaId\":1,\"calidad\":4}");

        mockMvc.perform(get("/api/v1/opiniones").header("X-Admin-Token", ADMIN))
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].calidad").value(4.0))
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].limpieza").doesNotExist())
                .andExpect(jsonPath("$.resumen.promedioPorRuta[0].conduccion").doesNotExist());
    }

    @Test
    void una_valoracion_fuera_de_rango_no_se_guarda() throws Exception {
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-f-6")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"limpieza\":9}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void una_opinion_solo_con_valoraciones_por_dimension_es_contenido_suficiente() throws Exception {
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-f-7")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"calificacion\",\"rutaId\":1,\"conduccion\":3}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", "nav-f-8")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"calificacion\",\"rutaId\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void opinar(String dispositivo, String cuerpo) throws Exception {
        mockMvc.perform(post("/api/v1/opiniones").header("X-Dispositivo-Id", dispositivo)
                        .contentType(APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    /** Reserva que el piloto marco como abordada: es lo que cuenta el panel. */
    private void abordajeDelPiloto(long rutaId, String cuando) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en,
                                              subio, abordaje_fuente, abordaje_en, abordado_por)
                SELECT ?, p.id, 'ABORDO', ?::timestamptz, ?::timestamptz + interval '5 minutes',
                       TRUE, 'CONDUCTOR', ?::timestamptz, 'conductor1'
                  FROM paradas p WHERE p.ruta_id = ? ORDER BY p.orden LIMIT 1
                """, "nav-" + cuando + "-" + rutaId, cuando, cuando, cuando, rutaId);
    }

    /** Lo dijo el pasajero, el piloto no lo marco: no cuenta. */
    private void abordajeDelPasajero(long rutaId, String cuando) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en,
                                              subio, abordaje_fuente, abordaje_en)
                SELECT ?, p.id, 'ABORDO', ?::timestamptz, ?::timestamptz + interval '5 minutes',
                       TRUE, 'PASAJERO', ?::timestamptz
                  FROM paradas p WHERE p.ruta_id = ? ORDER BY p.orden LIMIT 1
                """, "nav-pas-" + cuando + "-" + rutaId, cuando, cuando, cuando, rutaId);
    }
}
