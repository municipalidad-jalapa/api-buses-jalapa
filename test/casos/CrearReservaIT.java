package gt.muni.jalapa.ecoruta.demanda;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-306: POST /api/v1/reservas contra PostGIS real. */
class CrearReservaIT extends IntegracionPostgisTest {

    private static final String RUTA = "/api/v1/reservas";

    @Test
    void dentro_de_la_geocerca_responde_201_con_el_contrato() throws Exception {
        Coordenada parada = coordenadaDeParada(1L);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(UUID.randomUUID().toString(), 1L, parada.latitud(), parada.longitud())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.paradaId").value(1))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$", not(hasKey("dispositivoId"))))
                .andExpect(jsonPath("$", not(hasKey("creadoEn"))));
    }

    @Test
    void fuera_de_la_geocerca_responde_422() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(UUID.randomUUID().toString(), 1L, 14.0, -89.0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("acercarte")))
                .andExpect(jsonPath("$.path").value(RUTA));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_espera", Long.class)).isZero();
    }

    @Test
    void latitud_y_longitud_no_estan_invertidas() throws Exception {
        // Si se invirtieran al armar ST_MakePoint, el punto caeria lejos de Jalapa
        // y la geocerca rechazaria una coordenada que es exactamente la de la parada.
        Coordenada parada = coordenadaDeParada(1L);
        assertThat(parada.latitud()).isBetween(14.0, 15.0);
        assertThat(parada.longitud()).isBetween(-91.0, -89.0);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(UUID.randomUUID().toString(), 1L, parada.latitud(), parada.longitud())))
                .andExpect(status().isCreated());
    }

    @Test
    void parada_inexistente_responde_404_con_ApiError() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(UUID.randomUUID().toString(), 999_999L, 14.634878, -89.981202)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Parada")))
                .andExpect(jsonPath("$.path").value(RUTA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dispositivoId", "paradaId", "latitud", "longitud"})
    void campo_faltante_responde_400_con_ApiError(String campo) throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSinCampo(campo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value(RUTA));
    }

    @Test
    void segundo_registro_vigente_del_mismo_dispositivo_responde_422() throws Exception {
        String dispositivo = UUID.randomUUID().toString();
        Coordenada parada = coordenadaDeParada(1L);
        String cuerpo = cuerpo(dispositivo, 1L, parada.latitud(), parada.longitud());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reserva activa")));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registros_espera
                 WHERE dispositivo_id = ? AND estado IN ('ACTIVA', 'RENOVADA')
                """, Long.class, dispositivo)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABORDO", "CANCELADA", "EXPIRADA"})
    void tras_un_estado_no_vigente_se_puede_crear_otra_reserva(String estadoPrevio) throws Exception {
        String dispositivo = UUID.randomUUID().toString();
        // Fuera de la ventana de ritmo minimo (HU Desarrollo-95): este caso
        // aisla "el estado no bloquea" de "el ritmo no bloquea", que se prueba
        // aparte en dispositivo_que_creo_una_reserva_hace_instantes_no_puede_crear_otra...
        insertarReserva(dispositivo, 1L, estadoPrevio, "now() - interval '1 hour'");
        Coordenada parada = coordenadaDeParada(1L);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dispositivo, 1L, parada.latitud(), parada.longitud())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVA, ACTIVA",
            "ACTIVA, RENOVADA",
            "RENOVADA, ACTIVA"
    })
    void el_indice_parcial_impide_combinaciones_vigentes(String primero, String segundo) {
        String dispositivo = UUID.randomUUID().toString();
        insertarReserva(dispositivo, 1L, primero);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> insertarReserva(dispositivo, 2L, segundo))
                .hasMessageContaining("uq_registro_activo_por_dispositivo");
    }

    @Test
    void el_pasajero_anonimo_puede_invocar_sin_credencial() throws Exception {
        Coordenada parada = coordenadaDeParada(1L);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(UUID.randomUUID().toString(), 1L, parada.latitud(), parada.longitud())))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADA", "EXPIRADA", "ABORDO"})
    void dispositivo_que_creo_una_reserva_hace_instantes_no_puede_crear_otra_aunque_ya_no_este_vigente(
            String estadoPrevio) throws Exception {
        // El indice unico solo cuida "una vigente por dispositivo": esta reserva
        // ya no lo esta, pero se creo hace instantes (HU Desarrollo-95).
        String dispositivo = UUID.randomUUID().toString();
        insertarReserva(dispositivo, 1L, estadoPrevio);
        Coordenada parada = coordenadaDeParada(1L);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dispositivo, 1L, parada.latitud(), parada.longitud())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ritmo")));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM registros_espera WHERE dispositivo_id = ?", Long.class, dispositivo))
                .isEqualTo(1);
    }

    private Coordenada coordenadaDeParada(long paradaId) {
        Double lat = jdbc.queryForObject(
                "SELECT ST_Y(ubicacion) FROM paradas WHERE id = ?", Double.class, paradaId);
        Double lon = jdbc.queryForObject(
                "SELECT ST_X(ubicacion) FROM paradas WHERE id = ?", Double.class, paradaId);
        return new Coordenada(lat, lon);
    }

    private void insertarReserva(String dispositivo, long paradaId, String estado) {
        insertarReserva(dispositivo, paradaId, estado, "now()");
    }

    /**
     * @param creadoEnSql expresion SQL para {@code creado_en} (p.ej. {@code "now()"}
     *                     o {@code "now() - interval '1 hour'"}). Va directo al
     *                     texto de la consulta porque un parametro ligado no puede
     *                     representar una expresion; el valor no sale de las pruebas.
     */
    private void insertarReserva(String dispositivo, long paradaId, String estado, String creadoEnSql) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, %s, %s + interval '20 minutes')
                """.formatted(creadoEnSql, creadoEnSql),
                dispositivo, paradaId, estado);
    }

    private static String cuerpo(String dispositivoId, long paradaId, double lat, double lon) {
        return """
                {
                  "dispositivoId": "%s",
                  "paradaId": %d,
                  "latitud": %s,
                  "longitud": %s
                }
                """.formatted(dispositivoId, paradaId, lat, lon);
    }

    private static String cuerpoSinCampo(String campo) {
        return switch (campo) {
            case "dispositivoId" -> """
                    {"paradaId":1,"latitud":14.634878,"longitud":-89.981202}
                    """;
            case "paradaId" -> """
                    {"dispositivoId":"550e8400-e29b-41d4-a716-446655440000","latitud":14.634878,"longitud":-89.981202}
                    """;
            case "latitud" -> """
                    {"dispositivoId":"550e8400-e29b-41d4-a716-446655440000","paradaId":1,"longitud":-89.981202}
                    """;
            case "longitud" -> """
                    {"dispositivoId":"550e8400-e29b-41d4-a716-446655440000","paradaId":1,"latitud":14.634878}
                    """;
            default -> throw new IllegalArgumentException(campo);
        };
    }

    private record Coordenada(double latitud, double longitud) {
    }
}
