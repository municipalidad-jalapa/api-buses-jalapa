package gt.muni.jalapa.ecoruta.integraciones.traccar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

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
