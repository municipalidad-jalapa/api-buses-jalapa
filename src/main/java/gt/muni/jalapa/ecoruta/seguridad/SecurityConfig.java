package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.flota.seguridad.EquipoAuthFilter;
import gt.muni.jalapa.ecoruta.identidad.seguridad.ConductorJwtAuthFilter;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import gt.muni.jalapa.ecoruta.seguridad.ratelimit.RateLimitFilter;
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
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cadena de seguridad de la API.
 *
 * La API utiliza tokens enviados en cabeceras
 * y no mantiene sesiones en el servidor.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaApi(
            HttpSecurity http,
            EquipoAuthFilter equipoAuthFilter,
            ConductorJwtAuthFilter conductorJwtAuthFilter,
            AdminBootstrapFilter adminBootstrapFilter,
            RateLimitFilter rateLimitFilter,
            ApiErrorAuthenticationEntryPoint entryPoint,
            ApiErrorAccessDeniedHandler accessDenied,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
                /*
                 * API sin cookies de sesión.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(
                        sesion -> sesion.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * No utilizar HTTP Basic ni login por formulario.
                 */
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                /*
                 * Configuración CORS.
                 */
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )

                /*
                 * HU Desarrollo-95: cabeceras de seguridad.
                 *
                 * contentTypeOptions, frameOptions y cacheControl ya vienen
                 * activas por defecto en HttpSecurity; aqui solo se deja
                 * explicito lo que hace falta ajustar (HSTS) y lo que Spring
                 * Security no activa solo (Referrer-Policy, Permissions-Policy).
                 *
                 * La CSP se limita a /api/**: swagger-ui sirve su propio HTML
                 * con script inline y una CSP global lo rompe.
                 */
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permisos -> permisos.policy(
                                "geolocation=(), camera=(), microphone=(), payment=(), usb=()"))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new AntPathRequestMatcher("/api/**"),
                                new StaticHeadersWriter(
                                        "Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))))

                .authorizeHttpRequests(rutas -> rutas

                        /*
                         * Permitir que Spring procese correctamente
                         * los errores HTTP.
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
                         * TELEMETRÍA PÚBLICA
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
                         * CATÁLOGO DE RUTAS
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

                        /*
                         * SCRUM-306 / HU-134.
                         * Crear una reserva.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas"
                        )
                        .permitAll()

                        /*
                         * HU-135.
                         * Renovar su propia reserva.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas/*/renovacion"
                        )
                        .permitAll()

                        /*
                         * HU-124.
                         * Cancelar su propia reserva.
                         */
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/reservas/*"
                        )
                        .permitAll()

                        /*
                         * HU-76.
                         * El pasajero consulta el estado de
                         * una de sus reservas.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/reservas/*"
                        )
                        .permitAll()

                        /*
                         * HU-76.
                         * El pasajero declara que no abordó.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas/*/declaracion-no-abordo"
                        )
                        .permitAll()

                        /*
                         * HU-57.
                         * El pasajero responde al aviso
                         * de abordaje.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/reservas/*/abordaje"
                        )
                        .permitAll()

                        /*
                         * Registro del dispositivo
                         * para notificaciones.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/dispositivos/notificaciones"
                        )
                        .permitAll()

                        /*
                         * AUTENTICACIÓN DEL CONDUCTOR
                         *
                         * El conductor entrega aquí su
                         * idToken de Firebase.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/conductor"
                        )
                        .permitAll()

                        /*
                         * HU-76.
                         *
                         * El conductor marca una parada
                         * como atendida.
                         *
                         * Requiere un JWT válido que otorgue
                         * ROLE_CONDUCTOR.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/rutas/*/paradas/*/atendida"
                        )
                        .hasRole("CONDUCTOR")

                        /*
                         * INGESTA DE TELEMETRÍA
                         *
                         * Solo un equipo autenticado puede
                         * registrar posiciones.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/telemetria/posiciones"
                        )
                        .hasRole("EQUIPO")

                        /*
                         * PANEL DEL CONDUCTOR
                         */
                        .requestMatchers(
                                "/api/v1/conductor/**"
                        )
                        .hasRole("CONDUCTOR")

                        /*
                         * ADMINISTRACIÓN
                         */
                        .requestMatchers(
                                "/api/v1/admin/**"
                        )
                        .hasRole("ADMIN")

                        /*
                         * Cualquier endpoint que no tenga
                         * una regla explícita queda cerrado.
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
                 * Autenticación del equipo GPS.
                 */
                .addFilterBefore(
                        equipoAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                /*
                 * Autenticación JWT del conductor.
                 */
                .addFilterBefore(
                        conductorJwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        /*
         * BLOQUE PROVISIONAL.
         *
         * Permite ROLE_ADMIN mediante X-Admin-Token
         * mientras no exista completamente el filtro
         * definitivo de autenticación administrativa.
         */
        http.addFilterBefore(
                adminBootstrapFilter,
                EquipoAuthFilter.class
        );

        /*
         * HU Desarrollo-95: antes que cualquier autenticacion, incluido el
         * filtro provisional de arriba. Una peticion que ya viene mal de
         * ritmo no necesita gastar trabajo de autenticacion ni de base de
         * datos.
         */
        http.addFilterBefore(
                rateLimitFilter,
                AdminBootstrapFilter.class
        );

        return http.build();
    }

    /**
     * Configuración CORS de la API.
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
                        "Last-Event-ID",
                        "X-Dispositivo-Id"
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
     * EquipoAuthFilter solamente debe ejecutarse
     * dentro de la cadena de Spring Security.
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

    /**
     * ConductorJwtAuthFilter solamente debe ejecutarse
     * dentro de la cadena de Spring Security.
     */
    @Bean
    public FilterRegistrationBean<ConductorJwtAuthFilter>
    noRegistrarConductorJwtAuthFilter(
            ConductorJwtAuthFilter filtro
    ) {

        FilterRegistrationBean<ConductorJwtAuthFilter> registro =
                new FilterRegistrationBean<>(filtro);

        registro.setEnabled(false);

        return registro;
    }

    /**
     * RateLimitFilter solamente debe ejecutarse
     * dentro de la cadena de Spring Security.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter>
    noRegistrarRateLimitFilter(
            RateLimitFilter filtro
    ) {

        FilterRegistrationBean<RateLimitFilter> registro =
                new FilterRegistrationBean<>(filtro);

        registro.setEnabled(false);

        return registro;
    }
}
