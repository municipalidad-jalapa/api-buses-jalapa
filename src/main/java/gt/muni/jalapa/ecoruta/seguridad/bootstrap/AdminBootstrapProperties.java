package gt.muni.jalapa.ecoruta.seguridad.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * PROVISIONAL — TODO(SCRUM-134).
 *
 * @param bootstrapToken secreto de administracion. Ausente o vacio = sin acceso
 */
@ConfigurationProperties("ecoruta.admin")
public record AdminBootstrapProperties(String bootstrapToken) {

    /** Largo minimo para que el token no sea adivinable a mano. */
    public static final int LARGO_MINIMO = 32;

    public boolean estaConfigurado() {
        return StringUtils.hasText(bootstrapToken);
    }
}
