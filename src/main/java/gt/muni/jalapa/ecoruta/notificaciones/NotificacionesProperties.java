package gt.muni.jalapa.ecoruta.notificaciones;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Radios en metros, medidos con geography de PostGIS.
 *
 * @param radioAproximacionMetros primer aviso ("el bus esta por llegar")
 * @param radioLlegadaMetros      segundo aviso (pregunta de abordaje)
 */
@ConfigurationProperties("ecoruta.notificaciones")
public record NotificacionesProperties(int radioAproximacionMetros, int radioLlegadaMetros) {

    public NotificacionesProperties {
        radioAproximacionMetros = radioAproximacionMetros <= 0 ? 250 : radioAproximacionMetros;
        radioLlegadaMetros = radioLlegadaMetros <= 0 ? 40 : radioLlegadaMetros;
        if (radioLlegadaMetros >= radioAproximacionMetros) {
            radioLlegadaMetros = Math.max(1, radioAproximacionMetros / 6);
        }
    }
}
