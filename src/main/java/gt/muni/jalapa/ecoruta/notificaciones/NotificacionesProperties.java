package gt.muni.jalapa.ecoruta.notificaciones;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * Radios en metros, medidos con geography de PostGIS, y el margen del aviso
 * de vencimiento.
 *
 * @param radioAproximacionMetros  primer aviso ("el bus esta por llegar")
 * @param radioLlegadaMetros       segundo aviso (pregunta de abordaje)
 * @param avisoVencimientoSegundos cuanto antes de vencer se avisa que la
 *                                 reserva esta por vencer (QA 4.1). Por defecto 120.
 */
@ConfigurationProperties("ecoruta.notificaciones")
public record NotificacionesProperties(int radioAproximacionMetros, int radioLlegadaMetros,
                                       int avisoVencimientoSegundos) {

    /** Sin margen de vencimiento explicito: el de por defecto. */
    public NotificacionesProperties(int radioAproximacionMetros, int radioLlegadaMetros) {
        this(radioAproximacionMetros, radioLlegadaMetros, 0);
    }

    @ConstructorBinding
    public NotificacionesProperties {
        radioAproximacionMetros = radioAproximacionMetros <= 0 ? 250 : radioAproximacionMetros;
        radioLlegadaMetros = radioLlegadaMetros <= 0 ? 40 : radioLlegadaMetros;
        if (radioLlegadaMetros >= radioAproximacionMetros) {
            radioLlegadaMetros = Math.max(1, radioAproximacionMetros / 6);
        }
        avisoVencimientoSegundos = avisoVencimientoSegundos <= 0 ? 120 : avisoVencimientoSegundos;
    }

    public Duration avisoVencimiento() {
        return Duration.ofSeconds(avisoVencimientoSegundos);
    }
}
