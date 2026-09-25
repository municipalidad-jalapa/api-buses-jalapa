package gt.muni.jalapa.ecoruta.pasajeros.seguridad;

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Instala {@link PasajeroJwtAuthFilter} en TODAS las cadenas de seguridad
 * (SCRUM-26, bloque B).
 *
 * <p>Spring Security aplica a cada {@code HttpSecurity} los configuradores
 * registrados en {@code META-INF/spring.factories}. Asi el pasajero queda
 * autenticado tambien en la cadena principal —y recibe 403, no 401, si intenta
 * el panel del conductor o el municipal— sin tocar {@code SecurityConfig}.
 */
public class ConfiguradorSesionPasajero extends AbstractHttpConfigurer<ConfiguradorSesionPasajero, HttpSecurity> {

    @Override
    public void configure(HttpSecurity http) {
        ApplicationContext contexto = http.getSharedObject(ApplicationContext.class);
        if (contexto == null || contexto.getBeanNamesForType(PasajeroJwtAuthFilter.class).length == 0) {
            return;
        }
        http.addFilterBefore(contexto.getBean(PasajeroJwtAuthFilter.class), UsernamePasswordAuthenticationFilter.class);
    }
}
