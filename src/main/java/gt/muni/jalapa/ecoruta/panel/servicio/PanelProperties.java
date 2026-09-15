package gt.muni.jalapa.ecoruta.panel.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros del panel municipal (HU-79).
 *
 * @param umbralSinTransmitirMinutos si la ultima posicion es mas vieja que
 *                                   esto, el operador ve {@code transmitiendo
 *                                   = false} aunque el bus siga asignado.
 *                                   No se infiere del intervalo de ingesta:
 *                                   el refresco del tablero y el GPS del
 *                                   equipo no tienen por que coincidir
 */
@ConfigurationProperties("ecoruta.panel")
public record PanelProperties(int umbralSinTransmitirMinutos) {

    public PanelProperties {
        umbralSinTransmitirMinutos = umbralSinTransmitirMinutos <= 0
                ? 5
                : umbralSinTransmitirMinutos;
    }

    public Duration umbralSinTransmitir() {
        return Duration.ofMinutes(umbralSinTransmitirMinutos);
    }
}
