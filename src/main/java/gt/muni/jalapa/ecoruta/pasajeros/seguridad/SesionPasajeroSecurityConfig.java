package gt.muni.jalapa.ecoruta.pasajeros.seguridad;

import gt.muni.jalapa.ecoruta.seguridad.ApiErrorAccessDeniedHandler;
import gt.muni.jalapa.ecoruta.seguridad.ApiErrorAuthenticationEntryPoint;
import gt.muni.jalapa.ecoruta.seguridad.RutasPublicas;
import gt.muni.jalapa.ecoruta.seguridad.ratelimit.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Rutas de la sesion del pasajero (SCRUM-26, bloque B), en su propia cadena
 * para no tocar {@code SecurityConfig}. El filtro del pasajero lo instala
 * {@link ConfiguradorSesionPasajero} en esta y en todas las demas.
 *
 * <p>Abrir sesion y vincular tienen el cupo estricto de {@link RutasPublicas}
 * en {@link RateLimitFilter}: cada peticion crea o modifica filas.
 */
@Configuration
public class SesionPasajeroSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain cadenaSesionPasajero(HttpSecurity http,
                                                    ApiErrorAuthenticationEntryPoint entryPoint,
                                                    ApiErrorAccessDeniedHandler accessDenied,
                                                    RateLimitFilter rateLimitFilter,
                                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .securityMatcher("/api/v1/sesion/pasajero", "/api/v1/sesion/pasajero/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(rutas -> {
                    RutasPublicas.abrir(rutas);
                    rutas
                        .requestMatchers(HttpMethod.POST, "/api/v1/sesion/pasajero/vincular").hasRole("PASAJERO")
                        .anyRequest().denyAll();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                // Despues de CORS, para que un 429 conserve sus cabeceras, y antes de autenticar.
                .addFilterAfter(rateLimitFilter, CorsFilter.class);
        return http.build();
    }

    /** Solo dentro de las cadenas de seguridad, nunca en la cadena de servlets. */
    @Bean
    public FilterRegistrationBean<PasajeroJwtAuthFilter> noRegistrarPasajeroJwtAuthFilter(PasajeroJwtAuthFilter filtro) {
        FilterRegistrationBean<PasajeroJwtAuthFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
