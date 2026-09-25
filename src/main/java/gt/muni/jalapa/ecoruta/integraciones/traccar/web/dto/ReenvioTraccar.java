package gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReenvioTraccar(Posicion position, Dispositivo device) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Posicion(Long id, Long deviceId, Double latitude, Double longitude,
                           Double speed, Instant fixTime, Instant deviceTime, Instant serverTime,
                           String protocol, Boolean valid, Double altitude, Double course,
                           Double accuracy, Map<String, Object> attributes) {

        public Posicion(Long id, Long deviceId, Double latitude, Double longitude,
                        Double speed, Instant fixTime, Instant deviceTime) {
            this(id, deviceId, latitude, longitude, speed, fixTime, deviceTime,
                    null, null, null, null, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dispositivo(Long id, String uniqueId, String name, String status,
                              Instant lastUpdate, Map<String, Object> attributes) {

        public Dispositivo(Long id, String uniqueId, String name) {
            this(id, uniqueId, name, null, null, null);
        }
    }
}
