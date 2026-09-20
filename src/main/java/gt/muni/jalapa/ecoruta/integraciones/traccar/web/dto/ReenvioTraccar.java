package gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Un reenvio de Traccar en formato JSON ({@code forward.json=true}):
 * {@code {"position": {...}, "device": {...}}}. Solo se leen los campos que
 * EcoRuta usa; el resto se ignora.
 *
 * <p>Parte del contrato documentado en SCRUM-24. Si la captura real difiere,
 * el ajuste se hace aqui y en {@code LecturaTraccar}, no en la ingesta.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReenvioTraccar(Posicion position, Dispositivo device) {

    /** @param speed en la unidad de {@code ecoruta.integraciones.traccar.unidad-velocidad} (nudos por defecto) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Posicion(Long id, Long deviceId, Double latitude, Double longitude,
                           Double speed, Instant fixTime, Instant deviceTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dispositivo(Long id, String uniqueId, String name) {
    }
}
