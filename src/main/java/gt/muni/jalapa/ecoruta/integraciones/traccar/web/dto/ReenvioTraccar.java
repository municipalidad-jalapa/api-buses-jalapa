package gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo real que Traccar reenvia con {@code forward.type=json}
 * ({@code PositionForwarderJson}): un objeto {@code PositionData} con
 * {@code position} y {@code device} anidados. No es un objeto plano
 * {@code {deviceId, latitude, longitude}}.
 *
 * <p>La muestra versionada esta en
 * {@code src/test/resources/traccar/traccar-position-sample.json}.
 * Solo se leen los campos que EcoRuta usa; el resto se ignora.
 *
 * <p>En la captura real {@code position.id} llega en {@code 0} porque Traccar
 * reenvia antes de persistir la fila. No se debe usar ese 0 como identidad.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReenvioTraccar(Posicion position, Dispositivo device) {

    /**
     * @param speed en nudos (unidad interna de Traccar, {@code Position.speed}).
     *              EcoRuta la convierte segun {@code ecoruta.integraciones.traccar.unidad-velocidad}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Posicion(Long id, Long deviceId, Double latitude, Double longitude,
                           Double speed, Instant fixTime, Instant deviceTime, Instant serverTime,
                           String protocol, Boolean valid, Double altitude, Double course,
                           Double accuracy, Map<String, Object> attributes) {

        /** Constructor de pruebas: los campos que la captura trae de mas quedan nulos. */
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
