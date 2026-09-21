package gt.muni.jalapa.ecoruta.superadmin;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.pasajeros.PasajeroProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-26 (HU Desarrollo-146), bloque D: los cuatro roles y quien puede
 * invocar cada endpoint. Un rol que no corresponde recibe 403.
 */
class RolesYPermisosIT extends IntegracionPostgisTest {

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private PasajeroProperties pasajeroPropiedades;

    private String pasajero;
    private String piloto;
    private String municipalidad;
    private String superadmin;

    @BeforeEach
    void credenciales() {
        jdbc.update("DELETE FROM usuarios WHERE username IN ('muni-rolesit', 'super-rolesit')");
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid)
                VALUES ('muni-rolesit', 'ADMIN', TRUE, 'uid-muni-rolesit')
                """);
        jdbc.update("""
                INSERT INTO usuarios (username, rol, activo, firebase_uid)
                VALUES ('super-rolesit', 'SUPERADMIN', TRUE, 'uid-super-rolesit')
                """);

        pasajero = emisor.emitir("1", "pasajero", pasajeroPropiedades.sesion()).token();
        piloto = emisor.emitirParaConductor("conductor1").token();
        municipalidad = emisor.emitir("uid-muni-rolesit", EmisorDeJwt.ROL_ADMIN,
                java.time.Duration.ofMinutes(30)).token();
        superadmin = emisor.emitir("uid-super-rolesit", EmisorDeJwt.ROL_SUPERADMIN,
                java.time.Duration.ofMinutes(30)).token();
    }

    @Test
    void la_cuenta_superadmin_inicial_existe_y_nace_sin_identidad() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE rol = 'SUPERADMIN' AND username = 'superadmin'",
                Integer.class)).isEqualTo(1);
        // La contrasena y el uid viven en Firebase, nunca en el repositorio.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE username = 'superadmin' AND password_hash IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void solo_el_superadmin_administra_cuentas() throws Exception {
        mockMvc.perform(get("/api/v1/superadmin/cuentas").header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk());

        // La municipalidad mira el panel, pero no administra cuentas.
        mockMvc.perform(get("/api/v1/superadmin/cuentas").header(HttpHeaders.AUTHORIZATION, bearer(municipalidad)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/superadmin/cuentas").header(HttpHeaders.AUTHORIZATION, bearer(piloto)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/superadmin/cuentas").header(HttpHeaders.AUTHORIZATION, bearer(pasajero)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/superadmin/cuentas")).andExpect(status().isUnauthorized());
    }

    @Test
    void solo_el_superadmin_administra_rutas_paradas_y_vehiculos() throws Exception {
        mockMvc.perform(get("/api/v1/superadmin/rutas").header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/vehiculos").header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/superadmin/rutas").header(HttpHeaders.AUTHORIZATION, bearer(municipalidad)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/vehiculos").header(HttpHeaders.AUTHORIZATION, bearer(municipalidad)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_panel_municipal_lo_ven_la_municipalidad_y_el_superadmin_pero_no_los_demas() throws Exception {
        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, bearer(municipalidad)))
                .andExpect(status().isOk());
        // El SuperAdmin manda mas: tambien entra al panel.
        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, bearer(piloto)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/servicio").header(HttpHeaders.AUTHORIZATION, bearer(pasajero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_panel_del_conductor_es_del_piloto_y_de_nadie_mas() throws Exception {
        mockMvc.perform(get("/api/v1/conductor/ruta").header(HttpHeaders.AUTHORIZATION, bearer(municipalidad)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conductor/ruta").header(HttpHeaders.AUTHORIZATION, bearer(pasajero)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conductor/ruta").header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_piloto_no_marca_atendida_una_parada_de_otra_ruta() throws Exception {
        // conductor1 tiene asignada la ruta 1; la 2 es de otro.
        mockMvc.perform(post("/api/v1/rutas/2/paradas/9/atendida")
                        .header(HttpHeaders.AUTHORIZATION, bearer(piloto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_superadmin_crea_edita_y_desactiva_cuentas_pero_no_se_queda_sin_relevo() throws Exception {
        String creada = mockMvc.perform(post("/api/v1/superadmin/cuentas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"piloto-nuevo","rol":"CONDUCTOR","firebaseUid":"uid-piloto-nuevo","rutaId":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CONDUCTOR"))
                .andExpect(jsonPath("$.rutaId").value(1))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(creada.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/superadmin/cuentas/" + id + "/desactivacion")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // Queda 'superadmin' (de la migracion) ademas de 'super-rolesit': se puede.
        Long otro = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE username = 'super-rolesit'", Long.class);
        mockMvc.perform(post("/api/v1/superadmin/cuentas/" + otro + "/desactivacion")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isOk());

        // Ya solo queda uno activo: el sistema no se queda sin SuperAdmin.
        Long ultimo = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE rol = 'SUPERADMIN' AND activo", Long.class);
        mockMvc.perform(post("/api/v1/superadmin/cuentas/" + ultimo + "/desactivacion")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin)))
                .andExpect(status().isUnprocessableEntity());

        jdbc.update("DELETE FROM usuarios WHERE username = 'piloto-nuevo'");
        jdbc.update("UPDATE usuarios SET activo = TRUE WHERE rol = 'SUPERADMIN'");
    }

    @Test
    void el_superadmin_crea_una_ruta_con_su_parada() throws Exception {
        String ruta = mockMvc.perform(post("/api/v1/superadmin/rutas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ruta de prueba D","trazado":"LINESTRING(-89.99 14.63, -89.98 14.64)"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(true))
                .andReturn().getResponse().getContentAsString();
        long rutaId = Long.parseLong(ruta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/superadmin/paradas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Parada D","latitud":14.6330,"longitud":-89.9890,"orden":1,"rutaId":%d}"""
                                .formatted(rutaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitud").value(14.6330))
                .andExpect(jsonPath("$.rutaId").value(rutaId));

        jdbc.update("DELETE FROM paradas WHERE ruta_id = ?", rutaId);
        jdbc.update("DELETE FROM rutas WHERE id = ?", rutaId);
    }

    @Test
    void un_trazado_que_no_es_una_linea_no_se_guarda() throws Exception {
        mockMvc.perform(post("/api/v1/superadmin/rutas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superadmin))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Ruta torcida\",\"trazado\":\"esto no es WKT\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
