package gt.muni.jalapa.ecoruta.atrasos;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-26 (HU Desarrollo-146), bloque E: el piloto avisa un atraso y el aviso
 * llega a la pantalla del pasajero junto al tiempo estimado.
 */
class AvisoDeAtrasoIT extends IntegracionPostgisTest {

    @Autowired
    private EmisorDeJwt emisor;

    private String piloto;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM avisos_de_atraso");
        // conductor1 (V2) tiene asignada la ruta 1 desde V13.
        piloto = "Bearer " + emisor.emitirParaConductor("conductor1").token();
    }

    @Test
    void el_piloto_reporta_un_atraso_sobre_su_ruta_y_el_pasajero_lo_ve_con_el_eta() throws Exception {
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"motivo":"trafico","demoraMinutos":10,"comentario":"Cerrada la 1a Calle"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo").value("TRAFICO"))
                .andExpect(jsonPath("$.demoraMinutos").value(10))
                .andExpect(jsonPath("$.rutaId").value(1))
                .andExpect(jsonPath("$.vigenteHasta").exists());

        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atraso.motivo").value("TRAFICO"))
                .andExpect(jsonPath("$.atraso.demoraMinutos").value(10))
                .andExpect(jsonPath("$.atraso.comentario").value("Cerrada la 1a Calle"));

        // La ruta del otro bus no se contagia del aviso.
        mockMvc.perform(get("/api/v1/rutas/2/eta"))
                .andExpect(jsonPath("$.atraso").doesNotExist());
    }

    @Test
    void sin_atraso_reportado_el_eta_no_trae_ningun_aviso() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atraso").doesNotExist());
    }

    @Test
    void un_aviso_nuevo_reemplaza_al_anterior() throws Exception {
        reportar("trafico", 10);
        reportar("incidente", 25);

        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(jsonPath("$.atraso.motivo").value("INCIDENTE"))
                .andExpect(jsonPath("$.atraso.demoraMinutos").value(25));
        // El anterior no se borra: queda el historial de la ruta.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM avisos_de_atraso", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void el_piloto_retira_el_aviso_cuando_se_normaliza() throws Exception {
        reportar("trafico", 15);

        mockMvc.perform(delete("/api/v1/conductor/atrasos/vigente")
                        .header(HttpHeaders.AUTHORIZATION, piloto))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(jsonPath("$.atraso").doesNotExist());
        mockMvc.perform(get("/api/v1/conductor/atrasos/vigente")
                        .header(HttpHeaders.AUTHORIZATION, piloto))
                .andExpect(status().isNoContent());
    }

    @Test
    void un_aviso_vencido_deja_de_mostrarse_solo() throws Exception {
        reportar("trafico", 5);
        jdbc.update("UPDATE avisos_de_atraso SET vigente_hasta = now() - interval '1 minute'");

        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(jsonPath("$.atraso").doesNotExist());
    }

    @Test
    void el_motivo_y_la_demora_se_validan() throws Exception {
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"lluvia\",\"demoraMinutos\":10}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"trafico\",\"demoraMinutos\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"trafico\",\"demoraMinutos\":500}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void el_comentario_sale_neutralizado() throws Exception {
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"motivo":"incidente","demoraMinutos":8,"comentario":"<script>alert(1)</script>"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/rutas/1/eta"))
                .andExpect(jsonPath("$.atraso.comentario").value("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void solo_el_piloto_reporta_atrasos() throws Exception {
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"trafico\",\"demoraMinutos\":10}"))
                .andExpect(status().isUnauthorized());

        String pasajero = "Bearer " + emisor.emitir("1", "pasajero",
                java.time.Duration.ofDays(1)).token();
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, pasajero)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"trafico\",\"demoraMinutos\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_piloto_sin_ruta_asignada_no_puede_reportar() throws Exception {
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid)
                VALUES ('piloto-sin-ruta', 'CONDUCTOR', TRUE, 'uid-sin-ruta')
                """);
        String sinRuta = "Bearer " + emisor.emitirParaConductor("piloto-sin-ruta").token();

        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, sinRuta)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"trafico\",\"demoraMinutos\":10}"))
                .andExpect(status().isUnprocessableEntity());

        jdbc.update("DELETE FROM usuarios WHERE username = 'piloto-sin-ruta'");
    }

    @Test
    void retirar_un_aviso_que_no_existe_lo_dice_en_lugar_de_fallar_en_silencio() throws Exception {
        mockMvc.perform(delete("/api/v1/conductor/atrasos/vigente")
                        .header(HttpHeaders.AUTHORIZATION, piloto))
                .andExpect(status().isUnprocessableEntity());
    }

    private void reportar(String motivo, int minutos) throws Exception {
        mockMvc.perform(post("/api/v1/conductor/atrasos")
                        .header(HttpHeaders.AUTHORIZATION, piloto)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"%s\",\"demoraMinutos\":%d}".formatted(motivo, minutos)))
                .andExpect(status().isCreated());
    }
}
