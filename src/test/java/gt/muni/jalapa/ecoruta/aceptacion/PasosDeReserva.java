package gt.muni.jalapa.ecoruta.aceptacion;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Traduce los pasos de {@code reservar_un_lugar_en_la_parada.feature} (SCRUM-306). */
public class PasosDeReserva {

    private static final String RUTA = "/api/v1/reservas";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Dado("que existe una parada")
    public void que_existe_una_parada() {
        contexto.guardarParadaId(1L);
        contexto.guardarDispositivoId(UUID.randomUUID().toString());
    }

    @Y("el pasajero se encuentra dentro de la geocerca de la parada")
    public void el_pasajero_esta_dentro_de_la_geocerca() {
        Double lat = jdbc.queryForObject(
                "SELECT ST_Y(ubicacion) FROM paradas WHERE id = ?", Double.class, contexto.paradaId());
        Double lon = jdbc.queryForObject(
                "SELECT ST_X(ubicacion) FROM paradas WHERE id = ?", Double.class, contexto.paradaId());
        contexto.guardarCoordenadas(lat, lon);
    }

    @Y("el pasajero se encuentra fuera de la geocerca de la parada")
    public void el_pasajero_esta_fuera_de_la_geocerca() {
        contexto.guardarCoordenadas(14.0, -89.0);
    }

    @Y("el dispositivo no tiene una reserva vigente")
    public void el_dispositivo_no_tiene_reserva_vigente() {
        // La limpieza del escenario ya vacia registros_espera.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registros_espera
                 WHERE dispositivo_id = ? AND estado IN ('ACTIVA', 'RENOVADA')
                """, Long.class, contexto.dispositivoId())).isZero();
    }

    @Dado("que el dispositivo ya tiene una reserva en estado {string}")
    public void que_el_dispositivo_ya_tiene_reserva(String estado) {
        String dispositivo = UUID.randomUUID().toString();
        contexto.guardarDispositivoId(dispositivo);
        contexto.guardarParadaId(1L);
        Double lat = jdbc.queryForObject(
                "SELECT ST_Y(ubicacion) FROM paradas WHERE id = 1", Double.class);
        Double lon = jdbc.queryForObject(
                "SELECT ST_X(ubicacion) FROM paradas WHERE id = 1", Double.class);
        contexto.guardarCoordenadas(lat, lon);

        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, 1, ?, now(), now() + interval '20 minutes')
                """, dispositivo, estado);
    }

    @Dado("que la solicitud no contiene el campo {string}")
    public void que_la_solicitud_no_contiene_el_campo(String campo) {
        contexto.guardarCuerpoReservaPersonalizado(switch (campo) {
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
            default -> throw new IllegalArgumentException("Campo no contemplado: " + campo);
        });
    }

    @Dado("que se solicita una parada inexistente")
    public void que_se_solicita_una_parada_inexistente() {
        contexto.guardarDispositivoId(UUID.randomUUID().toString());
        contexto.guardarParadaId(999_999L);
        contexto.guardarCoordenadas(14.634878, -89.981202);
    }

    @Cuando("el pasajero indica que está esperando el bus")
    @Cuando("el mismo dispositivo intenta reservar nuevamente")
    @Cuando("se intenta crear la reserva")
    @Cuando("el pasajero intenta crear la reserva")
    public void el_pasajero_indica_que_esta_esperando() throws Exception {
        String cuerpo = contexto.cuerpoReservaPersonalizado() != null
                ? contexto.cuerpoReservaPersonalizado()
                : """
                {
                  "dispositivoId": "%s",
                  "paradaId": %d,
                  "latitud": %s,
                  "longitud": %s
                }
                """.formatted(
                        contexto.dispositivoId(),
                        contexto.paradaId(),
                        contexto.latitud(),
                        contexto.longitud());

        contexto.guardarRespuesta(mockMvc.perform(post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)));
    }

    @Entonces("la respuesta tiene estado HTTP {int}")
    public void la_respuesta_tiene_estado_http(int codigo) throws Exception {
        contexto.ultimaRespuesta().andExpect(status().is(codigo));
    }

    @Y("la reserva queda registrada en estado {string}")
    public void la_reserva_queda_registrada_en_estado(String estado) throws Exception {
        contexto.ultimaRespuesta().andExpect(jsonPath("$.estado").value(estado));
        assertThat(jdbc.queryForObject("""
                SELECT estado FROM registros_espera WHERE dispositivo_id = ?
                """, String.class, contexto.dispositivoId())).isEqualTo(estado);
    }

    @Y("la respuesta incluye el identificador, la parada y el momento de expiración")
    public void la_respuesta_incluye_campos_del_contrato() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.paradaId").value(contexto.paradaId().intValue()))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty());
    }

    @Y("se informa que debe acercarse a la parada")
    public void se_informa_que_debe_acercarse() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("acercarte")));
    }

    @Y("no se crea ninguna reserva")
    public void no_se_crea_ninguna_reserva() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_espera", Long.class)).isZero();
    }

    @Y("solamente permanece una reserva vigente para el dispositivo")
    public void solamente_permanece_una_reserva_vigente() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registros_espera
                 WHERE dispositivo_id = ? AND estado IN ('ACTIVA', 'RENOVADA')
                """, Long.class, contexto.dispositivoId())).isEqualTo(1);
    }

    @Y("la respuesta utiliza el formato de error de la API")
    public void la_respuesta_utiliza_el_formato_de_error() throws Exception {
        contexto.ultimaRespuesta()
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value(RUTA));
    }
}
