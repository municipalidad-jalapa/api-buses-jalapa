package gt.muni.jalapa.ecoruta.notificaciones;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import gt.muni.jalapa.ecoruta.notificaciones.servicio.EnviadorDeNotificaciones;
import gt.muni.jalapa.ecoruta.notificaciones.servicio.EnvioDeAvisoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-57 de extremo a extremo: telemetria real (mismo flujo que el simulador
 * de HU-66) dispara avisos. FCM se sustituye por el puerto
 * {@link EnviadorDeNotificaciones}: las pruebas comprueban que se llama con
 * el payload correcto, no la recepcion en un navegador (eso pide HTTPS).
 */
class AvisoDeAproximacionIT extends IntegracionPostgisTest {

    /** Parque Central despues de V6. */
    private static final double PARQUE_LAT = 14.634878;
    private static final double PARQUE_LON = -89.981202;
    /** ~100 m al norte: dentro del radio de aproximacion (250 m), fuera del de llegada (40 m). */
    private static final double APROX_LAT = 14.635776;
    private static final double APROX_LON = -89.981202;
    private static final double LEJOS_LAT = 14.50;
    private static final double LEJOS_LON = -90.20;

    @MockitoBean
    private EnviadorDeNotificaciones enviador;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private EmisorDeJwt emisor;

    @Autowired
    private ObjectMapper json;

    private String credencialEquipo;
    private Long paradaParque;
    private Long paradaLejana;

    @Override
    @BeforeEach
    protected void limpiarDatosDePrueba() {
        super.limpiarDatosDePrueba();
        reset(enviador);
        credencialEquipo = bearer(equipoService.emitir(busPiloto(), "Tableta HU-57"));
        paradaParque = jdbc.queryForObject(
                "SELECT id FROM paradas WHERE nombre = 'Parque Central' AND ruta_id = 1", Long.class);
        paradaLejana = jdbc.queryForObject(
                "SELECT id FROM paradas WHERE nombre = 'Llano Grande' AND ruta_id = 1", Long.class);
    }

    @Test
    void registrar_token_de_dispositivo_responde_204() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos/notificaciones")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"dispositivoId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                                 "tokenNotificacion":"token-fcm-1"}"""))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT token FROM dispositivos_notificacion WHERE dispositivo_id = ?",
                String.class, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .isEqualTo("token-fcm-1");
    }

    @Test
    void ac1_el_aviso_llega_solo_a_reservas_activas_de_esa_parada() throws Exception {
        String enParque = dispositivo();
        String tambienEnParque = dispositivo();
        String enOtraParada = dispositivo();
        registrarToken(enParque, "t-parque-1");
        registrarToken(tambienEnParque, "t-parque-2");
        registrarToken(enOtraParada, "t-lejana");
        crearReserva(enParque, paradaParque);
        crearReserva(tambienEnParque, paradaParque);
        crearReserva(enOtraParada, paradaLejana);

        ingerir(APROX_LAT, APROX_LON);

        verify(enviador, times(1)).enviar(argThat(a ->
                enParque.equals(a.dispositivoId()) && a.tipo() == TipoAviso.APROXIMACION));
        verify(enviador, times(1)).enviar(argThat(a ->
                tambienEnParque.equals(a.dispositivoId()) && a.tipo() == TipoAviso.APROXIMACION));
        verify(enviador, times(0)).enviar(argThat(a ->
                enOtraParada.equals(a.dispositivoId())));
    }

    @Test
    void ac2_un_mismo_acercamiento_no_duplica_el_aviso_pero_salir_y_volver_si()
            throws Exception {
        String dispositivo = dispositivo();
        registrarToken(dispositivo, "t-dup");
        crearReserva(dispositivo, paradaParque);

        ingerir(APROX_LAT, APROX_LON);
        ingerir(APROX_LAT + 0.0001, APROX_LON);
        verify(enviador, times(1)).enviar(argThat(a ->
                dispositivo.equals(a.dispositivoId()) && a.tipo() == TipoAviso.APROXIMACION));

        ingerir(LEJOS_LAT, LEJOS_LON);
        ingerir(APROX_LAT, APROX_LON);
        verify(enviador, times(2)).enviar(argThat(a ->
                dispositivo.equals(a.dispositivoId()) && a.tipo() == TipoAviso.APROXIMACION));
    }

    @Test
    void ac3_al_llegar_a_la_parada_se_manda_el_segundo_aviso() throws Exception {
        String dispositivo = dispositivo();
        registrarToken(dispositivo, "t-llegada");
        crearReserva(dispositivo, paradaParque);

        ingerir(APROX_LAT, APROX_LON);
        verify(enviador, times(1)).enviar(argThat(a -> a.tipo() == TipoAviso.APROXIMACION));
        verify(enviador, times(0)).enviar(argThat(a -> a.tipo() == TipoAviso.LLEGADA));

        ingerir(PARQUE_LAT, PARQUE_LON);
        verify(enviador, times(1)).enviar(argThat(a -> a.tipo() == TipoAviso.LLEGADA));
        verify(enviador, times(1)).enviar(argThat(a -> a.tipo() == TipoAviso.APROXIMACION));
    }

    @Test
    void ac4_el_pasajero_registra_si_subio() throws Exception {
        Long idSi = crearReserva(dispositivo(), paradaParque);
        Long idNo = crearReserva(dispositivo(), paradaParque);

        mockMvc.perform(post("/api/v1/reservas/" + idSi + "/abordaje")
                        .header("X-Dispositivo-Id", dispositivoDe(idSi))
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idSi.intValue()))
                .andExpect(jsonPath("$.estado").value("ABORDO"));

        mockMvc.perform(post("/api/v1/reservas/" + idNo + "/abordaje")
                        .header("X-Dispositivo-Id", dispositivoDe(idNo))
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        assertThat(jdbc.queryForObject(
                "SELECT subio FROM registros_espera WHERE id = ?", Boolean.class, idSi))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT abordaje_fuente FROM registros_espera WHERE id = ?", String.class, idSi))
                .isEqualTo("PASAJERO");
        assertThat(jdbc.queryForObject(
                "SELECT subio FROM registros_espera WHERE id = ?", Boolean.class, idNo))
                .isFalse();
    }

    @Test
    void ac5_el_dato_del_conductor_sobrescribe_al_del_pasajero() throws Exception {
        Long id = crearReserva(dispositivo(), paradaParque);
        mockMvc.perform(post("/api/v1/reservas/" + id + "/abordaje")
                        .header("X-Dispositivo-Id", dispositivoDe(id))
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":true}"))
                .andExpect(jsonPath("$.estado").value("ABORDO"));

        String jwt = emisor.emitirParaConductor("uid-hu57").token();
        mockMvc.perform(post("/api/v1/conductor/reservas/" + id + "/abordaje")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        assertThat(jdbc.queryForObject(
                "SELECT subio FROM registros_espera WHERE id = ?", Boolean.class, id))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT abordaje_fuente FROM registros_espera WHERE id = ?", String.class, id))
                .isEqualTo("CONDUCTOR");
    }

    @Test
    void ac4_abordaje_sobre_reserva_inactiva_responde_422() throws Exception {
        Long id = crearReserva(dispositivo(), paradaParque);
        mockMvc.perform(post("/api/v1/reservas/" + id + "/abordaje")
                        .header("X-Dispositivo-Id", dispositivoDe(id))
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservas/" + id + "/abordaje")
                        .header("X-Dispositivo-Id", dispositivoDe(id))
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("La reserva ya no esta activa"));
    }

    @Test
    void ac6_un_fallo_de_envio_se_registra_y_no_detiene_la_telemetria() throws Exception {
        String falla = dispositivo();
        String sigue = dispositivo();
        registrarToken(falla, "t-falla");
        registrarToken(sigue, "t-sigue");
        crearReserva(falla, paradaParque);
        crearReserva(sigue, paradaParque);

        doThrow(new EnvioDeAvisoException("FCM caido"))
                .when(enviador).enviar(argThat(a -> falla.equals(a.dispositivoId())));

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, credencialEquipo)
                        .contentType(APPLICATION_JSON)
                        .content(lote(APROX_LAT, APROX_LON, Instant.now())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.aceptadas").value(1));

        verify(enviador, times(1)).enviar(argThat(a -> sigue.equals(a.dispositivoId())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fallos_de_aviso", Long.class))
                .isGreaterThanOrEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void ac7_no_existe_aviso_de_salida_por_juntar_diez_pasajeros() throws Exception {
        for (int i = 0; i < 10; i++) {
            String dispositivo = dispositivo();
            registrarToken(dispositivo, "t-" + i);
            crearReserva(dispositivo, paradaParque);
        }

        ingerir(LEJOS_LAT, LEJOS_LON);
        verify(enviador, times(0)).enviar(org.mockito.ArgumentMatchers.any());

        ingerir(APROX_LAT, APROX_LON);
        verify(enviador, times(10)).enviar(argThat(a -> a.tipo() == TipoAviso.APROXIMACION));
        verify(enviador, times(0)).enviar(argThat(a ->
                a.titulo().toLowerCase().contains("diez")
                        || a.cuerpo().toLowerCase().contains("diez")));
    }

    private Long busPiloto() {
        return vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
    }

    private static String bearer(AltaDeEquipo alta) {
        return "Bearer " + alta.credencial().credencialCompleta();
    }

    private static String dispositivo() {
        return UUID.randomUUID().toString();
    }

    private void registrarToken(String dispositivoId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos/notificaciones")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"dispositivoId":"%s","tokenNotificacion":"%s"}"""
                                .formatted(dispositivoId, token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void ac4_el_pasajero_no_puede_responder_por_la_reserva_de_otro() throws Exception {
        Long id = crearReserva(dispositivo(), paradaParque);

        mockMvc.perform(post("/api/v1/reservas/" + id + "/abordaje")
                        .header("X-Dispositivo-Id", "otro-dispositivo")
                        .contentType(APPLICATION_JSON)
                        .content("{\"subio\":true}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "SELECT estado FROM registros_espera WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVA");
    }

    /** El dispositivo que creo la reserva: el unico que puede responder por ella. */
    private String dispositivoDe(Long reservaId) {
        return jdbc.queryForObject(
                "SELECT dispositivo_id FROM registros_espera WHERE id = ?", String.class, reservaId);
    }

    /**
     * Reserva por el contrato real de SCRUM-306. Exige coordenadas y aplica la
     * geocerca, asi que se reserva parado exactamente sobre la parada: las pruebas
     * de avisos no deben depender del radio de la geocerca.
     */
    private Long crearReserva(String dispositivoId, Long paradaId) throws Exception {
        Map<String, Object> punto = jdbc.queryForMap(
                "SELECT ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon FROM paradas WHERE id = ?",
                paradaId);
        String cuerpo = mockMvc.perform(post("/api/v1/reservas")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"dispositivoId":"%s","paradaId":%s,"latitud":%s,"longitud":%s}"""
                                .formatted(dispositivoId, paradaId, punto.get("lat"), punto.get("lon"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
    }

    private void ingerir(double lat, double lon) throws Exception {
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, credencialEquipo)
                        .contentType(APPLICATION_JSON)
                        .content(lote(lat, lon, Instant.now())))
                .andExpect(status().isAccepted());
    }

    private static String lote(double latitud, double longitud, Instant cuando) {
        return """
                {"posiciones": [
                  {"latitud": %s, "longitud": %s, "velocidadKmh": 18, "timestamp": "%s"}
                ]}""".formatted(latitud, longitud, cuando);
    }
}
