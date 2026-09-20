package gt.muni.jalapa.ecoruta.integraciones.traccar.seguridad;

import gt.muni.jalapa.ecoruta.integraciones.traccar.TraccarProperties;
import gt.muni.jalapa.ecoruta.seguridad.ApiErrorAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadena de seguridad propia de la integracion con Traccar (SCRUM-24).
 *
 * <p>Separada de la cadena de la API a proposito: aqui solo corre el filtro del
 * token de integracion. Asi un {@code Bearer eq_...} en esta ruta no autentica
 * a nadie (401, no 403), y el token de Traccar no existe para el resto de la API.
 */
@Configuration
@Slf4j
public class TraccarSecurityConfig {

    public static final String RUTAS = "/api/v1/integraciones/traccar/**";

    @Bean
    @Order(1)
    public SecurityFilterChain cadenaTraccar(HttpSecurity http, TraccarProperties traccar,
                                             ApiErrorAuthenticationEntryPoint entryPoint) throws Exception {
        if (!traccar.estaConfigurada()) {
            log.info("ecoruta.integraciones.traccar.token no esta configurado: la integracion "
                    + "con Traccar rechaza todo con 401.");
        } else if (traccar.token().length() < TraccarProperties.LARGO_MINIMO_TOKEN) {
            throw new IllegalStateException("ecoruta.integraciones.traccar.token debe tener al menos %d caracteres"
                    .formatted(TraccarProperties.LARGO_MINIMO_TOKEN));
        }

        http
                .securityMatcher(RUTAS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.POST, "/api/v1/integraciones/traccar/posiciones")
                        .hasRole("INTEGRACION_TRACCAR")
                        .anyRequest().denyAll())
                // Toda falta de credencial valida es 401: aqui no hay otro rol posible.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((peticion, respuesta, ex) ->
                                entryPoint.commence(peticion, respuesta, null)))
                // Se construye aqui y no como @Bean: un Filter @Bean lo instalaria
                // Boot tambien en la cadena de servlets, para todas las rutas.
                .addFilterBefore(new TraccarTokenFilter(traccar.token()), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
