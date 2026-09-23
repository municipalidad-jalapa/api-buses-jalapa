package gt.muni.jalapa.ecoruta.opiniones.seguridad;

import gt.muni.jalapa.ecoruta.identidad.seguridad.AdminJwtAuthFilter;
import gt.muni.jalapa.ecoruta.identidad.seguridad.ConductorJwtAuthFilter;
import gt.muni.jalapa.ecoruta.seguridad.ApiErrorAccessDeniedHandler;
import gt.muni.jalapa.ecoruta.seguridad.ApiErrorAuthenticationEntryPoint;
import gt.muni.jalapa.ecoruta.seguridad.RutasPublicas;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import gt.muni.jalapa.ecoruta.seguridad.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cadena de seguridad de las opiniones (SCRUM-26, bloque A).
 *
 * <p>En un archivo propio y no en {@code SecurityConfig}, que tienen abierto
 * otras ramas: asi este modulo no choca con ellas. Registrar es publico;
 * listar y atender exige rol administrador. Un conductor recibe 403 y quien no
 * trae sesion, 401.
 *
 * <p>El registro publico queda bajo {@link RateLimitFilter} con el cupo
 * estricto de {@link RutasPublicas}: el limite por navegador depende de una
 * cabecera que pone el cliente y por si solo se evade.
 */
@Configuration
public class OpinionesSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain cadenaOpiniones(HttpSecurity http,
                                               ConductorJwtAuthFilter conductorJwtAuthFilter,
                                               AdminJwtAuthFilter adminJwtAuthFilter,
                                               AdminBootstrapFilter adminBootstrapFilter,
                                               RateLimitFilter rateLimitFilter,
                                               ApiErrorAuthenticationEntryPoint entryPoint,
                                               ApiErrorAccessDeniedHandler accessDenied,
                                               CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .securityMatcher("/api/v1/opiniones", "/api/v1/opiniones/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(rutas -> {
                    RutasPublicas.abrir(rutas);
                    rutas
                        .requestMatchers(HttpMethod.GET, "/api/v1/opiniones").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/opiniones/*/atendida").hasRole("ADMIN")
                        .anyRequest().denyAll();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .addFilterBefore(conductorJwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminJwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // PROVISIONAL de SCRUM-142, igual que en la cadena de la API.
                .addFilterBefore(adminBootstrapFilter, ConductorJwtAuthFilter.class)
                // Despues de CORS, para que un 429 conserve sus cabeceras, y antes de autenticar.
                .addFilterAfter(rateLimitFilter, CorsFilter.class);
        return http.build();
    }
}
