package gt.muni.jalapa.ecoruta.integraciones.traccar;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-24 (HU Desarrollo-144): recibir en la API las posiciones que reenvia Traccar. */
class RecepcionTraccarIT extends IntegracionPostgisTest {

    static final String RUTA = "/api/v1/integraciones/traccar/posiciones";
    static final String IMEI = "860000000000001";

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    private AltaDeEquipo equipo;

    @BeforeEach
    void asociarDispositivo() {
        Long bus = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        equipo = equipoService.emitir(bus, "GPS 103A de prueba");
        jdbc.update("""
                INSERT INTO dispositivos_externos (identificador, equipo_id)
                SELECT ?, id FROM equipos WHERE vehiculo_id = ? AND estado = 'ACTIVO'
                """, IMEI, bus);
    }

    // --- criterio 1: credencial de integracion ---------------------------------

    @Test
    void sin_cabecera_o_con_un_token_distinto_responde_401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(APPLICATION_JSON).content(reenvio(1, IMEI, 10, Instant.now())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(post(RUTA).header("X-Traccar-Token", "otro-token-cualquiera-de-32-caracteres!")
                        .contentType(APPLICATION_JSON).content(reenvio(1, IMEI, 10, Instant.now())))
                .andExpect(status().isUnauthorized());
        assertThat(filas()).isZero();
    }

    @Test
    void la_credencial_del_equipo_a_bordo_no_sirve_para_la_integracion() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON).content(reenvio(1, IMEI, 10, Instant.now())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void el_token_de_traccar_no_sirve_para_la_telemetria_del_equipo() throws Exception {
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header("X-Traccar-Token", TRACCAR)
                        .contentType(APPLICATION_JSON)
                        .content("{\"posiciones\":[{\"latitud\":14.63,\"longitud\":-89.98,\"timestamp\":\"%s\"}]}"
                                .formatted(Instant.now())))
                .andExpect(status().isUnauthorized());
    }

    // --- criterio 2: dispositivo -> equipo -> vehiculo -------------------------

    @Test
    void una_posicion_valida_se_registra_para_el_vehiculo_del_equipo_asociado() throws Exception {
        enviar(reenvio(501, IMEI, 10, Instant.now()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recibidas").value(1))
                .andExpect(jsonPath("$.aceptadas").value(1))
                .andExpect(jsonPath("$.descartadas").value(0));

        mockMvc.perform(get("/api/v1/telemetria/posicion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiculo").value("BUS-01"))
                .andExpect(jsonPath("$.latitud").value(14.6335));
    }

    @Test
    void un_dispositivo_no_asociado_responde_422_y_no_registra_nada() throws Exception {
        enviar(reenvio(1, "999999999999999", 10, Instant.now()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void si_un_dispositivo_del_lote_no_esta_asociado_no_se_registra_ninguno() throws Exception {
        enviar("[" + reenvio(1, IMEI, 10, Instant.now()) + "," + reenvio(2, "999", 10, Instant.now()) + "]")
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    // --- criterio 3: validacion y unidades --------------------------------------

    @Test
    void latitud_fuera_de_rango_o_sin_fecha_responde_422() throws Exception {
        enviar("""
                {"device":{"uniqueId":"%s"},"position":{"id":7,"latitude":95,"longitude":-89.98,"fixTime":"%s"}}"""
                .formatted(IMEI, Instant.now()))
                .andExpect(status().isUnprocessableEntity());
        enviar("""
                {"device":{"uniqueId":"%s"},"position":{"id":8,"latitude":14.63,"longitude":-89.98}}"""
                .formatted(IMEI))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void longitud_fuera_de_rango_responde_422() throws Exception {
        enviar("""
                {"device":{"uniqueId":"%s"},"position":{"id":7,"latitude":14.63,"longitude":-181,"fixTime":"%s"}}"""
                .formatted(IMEI, Instant.now()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void un_cuerpo_mal_formado_responde_400() throws Exception {
        enviar("{no es json").andExpect(status().isBadRequest());
        enviar("\"texto\"").andExpect(status().isBadRequest());
    }

    @Test
    void la_muestra_real_con_dispositivo_asociado_responde_202() throws Exception {
        enviar(muestraReal(IMEI, Instant.now(), 14.634001, -89.9871))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recibidas").value(1))
                .andExpect(jsonPath("$.aceptadas").value(1))
                .andExpect(jsonPath("$.descartadas").value(0));
    }

    @Test
    void la_muestra_real_con_dispositivo_no_asociado_responde_422() throws Exception {
        enviar(muestraReal("999999999999999", Instant.now(), 14.634001, -89.9871))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void la_muestra_real_con_coordenadas_invalidas_responde_422() throws Exception {
        enviar(muestraReal(IMEI, Instant.now(), 95.0, -89.9871))
                .andExpect(status().isUnprocessableEntity());
        assertThat(filas()).isZero();
    }

    @Test
    void la_velocidad_en_nudos_se_guarda_en_kilometros_por_hora() throws Exception {
        enviar(reenvio(9, IMEI, 10, Instant.now())).andExpect(status().isAccepted());

        Double kmh = jdbc.queryForObject("SELECT velocidad_kmh FROM posiciones_historicas", Double.class);
        assertThat(kmh).isEqualTo(18.52, org.assertj.core.data.Offset.offset(0.001));
    }

    // --- criterio 4: ventana de 12 h ---------------------------------------------

    @Test
    void una_lectura_fuera_de_la_ventana_de_doce_horas_se_descarta() throws Exception {
        enviar(reenvio(10, IMEI, 10, Instant.now().minus(Duration.ofHours(13))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(0))
                .andExpect(jsonPath("$.descartadas").value(1));
        assertThat(filas()).isZero();
    }

    // --- criterios 5 y 7: sin duplicados, respuesta resumida ---------------------

    @Test
    void un_reenvio_repetido_no_genera_una_posicion_duplicada() throws Exception {
        String mismo = reenvio(77, IMEI, 10, Instant.now());
        enviar(mismo).andExpect(jsonPath("$.aceptadas").value(1));
        enviar(mismo)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recibidas").value(1))
                .andExpect(jsonPath("$.aceptadas").value(0))
                .andExpect(jsonPath("$.descartadas").value(1));
        assertThat(filas()).isEqualTo(1);
    }

    @Test
    void un_lote_mixto_resume_recibidas_aceptadas_y_descartadas() throws Exception {
        enviar(reenvio(100, IMEI, 10, Instant.now())).andExpect(status().isAccepted());

        enviar("[" + String.join(",",
                        reenvio(100, IMEI, 10, Instant.now()),                              // repetida
                        reenvio(101, IMEI, 10, Instant.now().minusSeconds(30)),            // nueva
                        reenvio(102, IMEI, 10, Instant.now().minus(Duration.ofHours(20))), // fuera de ventana
                        reenvio(101, IMEI, 10, Instant.now().minusSeconds(30))) + "]")     // repetida en el lote
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recibidas").value(4))
                .andExpect(jsonPath("$.aceptadas").value(1))
                .andExpect(jsonPath("$.descartadas").value(3));
        assertThat(filas()).isEqualTo(2);
    }

    @Test
    void la_clave_de_origen_queda_registrada_y_la_base_impide_duplicarla() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_posicion_clave_origen'
                """, Integer.class)).isEqualTo(1);
    }

    private ResultActions enviar(String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA)
                .header("X-Traccar-Token", TRACCAR)
                .contentType(APPLICATION_JSON)
                .content(cuerpo));
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Integer.class);
    }

    /** Formato de reenvio de Traccar (forward.type=json). La velocidad va en nudos. */
    static String reenvio(long idPosicion, String uniqueId, double nudos, Instant fixTime) {
        return """
                {"position":{"id":%d,"deviceId":3,"protocol":"gt06","latitude":14.6335,"longitude":-89.9885,
                 "speed":%s,"course":90,"fixTime":"%s","deviceTime":"%s","valid":true,"attributes":{"ignition":true}},
                 "device":{"id":3,"name":"BUS-01","uniqueId":"%s","status":"online"}}"""
                .formatted(idPosicion, nudos, fixTime, fixTime, uniqueId);
    }

    /**
     * Parte de la captura real. Se actualizan uniqueId, coordenadas y fechas para
     * el caso de prueba: la estructura anidada no se inventa.
     */
    private static String muestraReal(String uniqueId, Instant cuando, double latitud, double longitud)
            throws Exception {
        String original = new String(
                RecepcionTraccarIT.class.getResourceAsStream("/traccar/traccar-position-sample.json").readAllBytes(),
                StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules();
        com.fasterxml.jackson.databind.node.ObjectNode raiz =
                (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(original);
        com.fasterxml.jackson.databind.node.ObjectNode posicion =
                (com.fasterxml.jackson.databind.node.ObjectNode) raiz.get("position");
        com.fasterxml.jackson.databind.node.ObjectNode device =
                (com.fasterxml.jackson.databind.node.ObjectNode) raiz.get("device");
        posicion.put("latitude", latitud);
        posicion.put("longitude", longitud);
        posicion.put("fixTime", cuando.toString());
        posicion.put("deviceTime", cuando.toString());
        posicion.put("serverTime", cuando.toString());
        device.put("uniqueId", uniqueId);
        return json.writeValueAsString(raiz);
    }
}
