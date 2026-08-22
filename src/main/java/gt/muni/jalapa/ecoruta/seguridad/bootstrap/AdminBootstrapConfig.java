package gt.muni.jalapa.ecoruta.seguridad.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PROVISIONAL — TODO(SCRUM-134). Ver {@link AdminBootstrapFilter}. */
@Configuration
@Slf4j
public class AdminBootstrapConfig {

    @Bean
    public AdminBootstrapFilter adminBootstrapFilter(AdminBootstrapProperties propiedades) {
        if (!propiedades.estaConfigurado()) {
            log.info("ecoruta.admin.bootstrap-token no esta configurado: los endpoints "
                    + "/api/v1/admin/** quedan cerrados para todos.");
            return new AdminBootstrapFilter(null);
        }
        if (propiedades.bootstrapToken().length() < AdminBootstrapProperties.LARGO_MINIMO) {
            throw new IllegalStateException(
                    "ecoruta.admin.bootstrap-token debe tener al menos %d caracteres"
                            .formatted(AdminBootstrapProperties.LARGO_MINIMO));
        }

        log.warn("ATENCION: AdminBootstrapFilter ACTIVO. Mecanismo provisional de "
                + "SCRUM-142 que debe desaparecer con SCRUM-134 (Firebase). "
                + "No usar en produccion.");
        return new AdminBootstrapFilter(propiedades.bootstrapToken());
    }

    /**
     * Todo bean Filter lo instala Boot tambien en la cadena de servlets. Este solo
     * debe vivir dentro de la cadena de Spring Security.
     */
    @Bean
    public FilterRegistrationBean<AdminBootstrapFilter> noRegistrarAdminBootstrapFilter(
            AdminBootstrapFilter filtro) {
        FilterRegistrationBean<AdminBootstrapFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
