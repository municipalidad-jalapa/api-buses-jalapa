package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.flota.seguridad.EquipoAuthFilter;
import gt.muni.jalapa.ecoruta.identidad.seguridad.AdminJwtAuthFilter;
import gt.muni.jalapa.ecoruta.identidad.seguridad.ConductorJwtAuthFilter;
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
            AdminJwtAuthFilter adminJwtAuthFilter,
            AdminBootstrapFilter adminBootstrapFilter,
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
                         * AUTENTICACIÓN DEL PANEL MUNICIPAL (SCRUM-173)
                         *
                         * Mismo mecanismo que el conductor: idToken de
                         * Firebase a cambio del JWT de administrador.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/admin"
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
                )

                /*
                 * Autenticación JWT del administrador municipal (SCRUM-173).
                 */
                .addFilterBefore(
                        adminJwtAuthFilter,
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
     * AdminJwtAuthFilter solamente debe ejecutarse
     * dentro de la cadena de Spring Security (SCRUM-173).
     */
    @Bean
    public FilterRegistrationBean<AdminJwtAuthFilter>
    noRegistrarAdminJwtAuthFilter(
            AdminJwtAuthFilter filtro
    ) {

        FilterRegistrationBean<AdminJwtAuthFilter> registro =
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
}
