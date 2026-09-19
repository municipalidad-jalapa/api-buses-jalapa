package gt.muni.jalapa.ecoruta.integraciones.traccar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Integracion con Traccar (SCRUM-24, HU Desarrollo-144).
 *
 * @param token           secreto que Traccar envia en {@code X-Traccar-Token}.
 *                        Vacio = nadie entra: la integracion falla cerrada
 * @param unidadVelocidad unidad de {@code position.speed} en el reenvio.
 *                        Traccar usa nudos salvo que se configure otra cosa
 */
@ConfigurationProperties("ecoruta.integraciones.traccar")
public record TraccarProperties(String token, UnidadVelocidad unidadVelocidad) {

    public static final int LARGO_MINIMO_TOKEN = 32;

    public TraccarProperties {
        unidadVelocidad = unidadVelocidad == null ? UnidadVelocidad.NUDOS : unidadVelocidad;
    }

    public boolean estaConfigurada() {
        return StringUtils.hasText(token);
    }

    public enum UnidadVelocidad {
        NUDOS(1.852),
        KMH(1.0),
        MS(3.6);

        private final double aKmh;

        UnidadVelocidad(double aKmh) {
            this.aKmh = aKmh;
        }

        public Double aKmh(Double valor) {
            return valor == null ? null : valor * aKmh;
        }
    }
}
