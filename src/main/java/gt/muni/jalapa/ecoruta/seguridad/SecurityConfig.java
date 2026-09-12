package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.flota.seguridad.EquipoAuthFilter;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cadena de seguridad de la API.
 *
 * La API trabaja con tokens en cabecera y no utiliza
 * sesiones del servidor.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaApi(
            HttpSecurity http,
            EquipoAuthFilter equipoAuthFilter,
            AdminBootstrapFilter adminBootstrapFilter,
            ApiErrorAuthenticationEntryPoint entryPoint,
            ApiErrorAccessDeniedHandler accessDenied,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
                // API sin cookies de sesion.
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(
                        sesion -> sesion.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // No utilizar login por formulario ni HTTP Basic.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // Configuracion CORS.
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )

                .authorizeHttpRequests(rutas -> rutas

                        /*
                         * /error debe ser publico para que los errores
                         * mantengan su codigo HTTP correcto.
                         */
                        .requestMatchers("/error")
                        .permitAll()

                        /*
                         * Actuator.
                         */
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        )
                        .permitAll()

                        /*
                         * Swagger / OpenAPI.
                         */
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        )
                        .permitAll()

                        /*
                         * TELEMETRIA PUBLICA
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/telemetria/posicion"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/telemetria/stream"
                        )
                        .permitAll()

                        /*
                         * CATALOGO DE RUTAS
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/rutas",
                                "/api/v1/rutas/**"
                        )
                        .permitAll()

                        /*
                         * RESERVAS DEL PASAJERO
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas"
                        )
                        .permitAll()

                        /*
                         * HU-76:
                         * consultar una reserva.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/reservas/{reservaId}"
                        )
                        .permitAll()

                        /*
                         * HU-76:
                         * pasajero declara que no abordo.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas/{reservaId}/declaracion-no-abordo"
                        )
                        .permitAll()

                        .requestMatchers(
                                "/api/v1/demanda/**"
                        )
                        .permitAll()

                        /*
                         * HU-76
                         *
                         * El conductor marca una parada como atendida.
                         * Solamente ROLE_CONDUCTOR puede utilizar
                         * este endpoint.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/rutas/{rutaId}/paradas/{paradaId}/atendida"
                        )
                        .hasRole("CONDUCTOR")

                        /*
                         * INGESTA DE TELEMETRIA
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/telemetria/posiciones"
                        )
                        .hasRole("EQUIPO")

                        /*
                         * ADMINISTRACION
                         */
                        .requestMatchers(
                                "/api/v1/admin/**"
                        )
                        .hasRole("ADMIN")

                        /*
                         * Cualquier endpoint que no tenga una
                         * regla explicita queda bloqueado.
                         */
                        .anyRequest()
                        .denyAll()
                )

                /*
                 * Respuestas uniformes para:
                 * 401 Unauthorized
                 * 403 Forbidden
                 */
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied)
                )

                /*
                 * Autenticacion de equipos.
                 */
                .addFilterBefore(
                        equipoAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        /*
         * Provisional:
         * permite ROLE_ADMIN mediante X-Admin-Token
         * mientras no exista completamente la
         * autenticacion de personas.
         */
        http.addFilterBefore(
                adminBootstrapFilter,
                EquipoAuthFilter.class
        );

        return http.build();
    }

    /**
     * Configuracion CORS de la API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            CorsProperties propiedades
    ) {

        CorsConfiguration configuracion =
                new CorsConfiguration();

        configuracion.setAllowedOrigins(
                propiedades.origenesPermitidos()
        );

        configuracion.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuracion.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Last-Event-ID"
                )
        );

        configuracion.setExposedHeaders(
                List.of(
                        "Cache-Control",
                        "Content-Type"
                )
        );

        configuracion.setAllowCredentials(true);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente =
                new UrlBasedCorsConfigurationSource();

        fuente.registerCorsConfiguration(
                "/api/**",
                configuracion
        );

        return fuente;
    }

    /**
     * EquipoAuthFilter solo debe ejecutarse
     * dentro de Spring Security.
     */
    @Bean
    public FilterRegistrationBean<EquipoAuthFilter>
    noRegistrarEquipoAuthFilter(
            EquipoAuthFilter filtro
    ) {

        FilterRegistrationBean<EquipoAuthFilter> registro =
                new FilterRegistrationBean<>(filtro);

        registro.setEnabled(false);

        return registro;
    }
}