package gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReenvioTraccarDeserializacionTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    void la_muestra_real_no_es_un_objeto_plano() throws Exception {
        JsonNode raiz = JSON.readTree(muestra());

        assertThat(raiz.has("deviceId")).isFalse();
        assertThat(raiz.has("latitude")).isFalse();
        assertThat(raiz.has("longitude")).isFalse();
        assertThat(raiz.path("position").isObject()).isTrue();
        assertThat(raiz.path("device").isObject()).isTrue();
    }

    @Test
    void deserializa_dispositivo_coordenadas_velocidad_y_fecha_de_la_muestra_real() throws Exception {
        ReenvioTraccar reenvio = JSON.readValue(muestra(), ReenvioTraccar.class);

        assertThat(reenvio.device()).isNotNull();
        assertThat(reenvio.device().uniqueId()).isEqualTo("860000000000001");
        assertThat(reenvio.device().id()).isEqualTo(1L);
        assertThat(reenvio.device().name()).isEqualTo("BUS-01-prueba");

        ReenvioTraccar.Posicion posicion = reenvio.position();
        assertThat(posicion).isNotNull();
        assertThat(posicion.latitude()).isEqualTo(14.634001);
        assertThat(posicion.longitude()).isEqualTo(-89.9871);
        assertThat(posicion.speed()).isEqualTo(12.5);
        assertThat(posicion.fixTime()).isEqualTo(Instant.parse("2026-09-21T02:41:35.000Z"));
        assertThat(posicion.deviceTime()).isEqualTo(Instant.parse("2026-09-21T02:41:35.000Z"));
        assertThat(posicion.serverTime()).isEqualTo(Instant.parse("2026-09-21T02:41:35.747Z"));
        assertThat(posicion.protocol()).isEqualTo("osmand");
        assertThat(posicion.id()).isZero();
        assertThat(posicion.attributes()).containsEntry("hdop", 0.7);
    }

    private static InputStream muestra() {
        InputStream recurso = ReenvioTraccarDeserializacionTest.class
                .getResourceAsStream("/traccar/traccar-position-sample.json");
        assertThat(recurso).as("muestra real versionada").isNotNull();
        return recurso;
    }
}
