package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.flota.seguridad.EquipoAuthFilter;
import gt.muni.jalapa.ecoruta.seguridad.bootstrap.AdminBootstrapFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
 * <p>Hasta ahora no existia ninguna, asi que Spring Boot ponia HTTP Basic con
 * contrasena generada sobre absolutamente todo.
 *
 * <p>No hay una historia propia de SecurityConfig: esta implicito en SCRUM-134
 * (login de personas con Firebase), SCRUM-142 (credencial de equipo) y SCRUM-274
 * (CORS), las tres de owner-D4. Por eso este archivo se escribe pensando en que
 * las otras dos lleguen despues sin reescribirlo.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaApi(HttpSecurity http,
                                         EquipoAuthFilter equipoAuthFilter,
                                         AdminBootstrapFilter adminBootstrapFilter,
                                         ApiErrorAuthenticationEntryPoint entryPoint,
                                         ApiErrorAccessDeniedHandler accessDenied,
                                         CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http
                // API con token en cabecera y sin cookies de sesion: CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin deshabilitarlos, Boot monta Basic y formulario sobre todo.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Gancho para SCRUM-274: hoy la fuente no permite ningun origen.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(rutas -> rutas
                        // /error tiene que ser publico: en Spring Security 6 el
                        // AuthorizationFilter filtra tambien el dispatch ERROR, y sin
                        // esto todo error se vuelve un 403 sin cuerpo. Es la causa mas
                        // comun de "por que mi 401 es un 403".
                        .requestMatchers("/error").permitAll()

                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Publico para el pasajero anonimo. Se permiten ya las rutas que
                        // SCRUM-130/131/132/133 y SCRUM-139/140 declaran publicas, para
                        // que el denyAll() de abajo no se convierta en una mina que deje
                        // en 403 las ramas de D1, D2 y D3 al mergear.
                        .requestMatchers(HttpMethod.GET, "/api/v1/telemetria/posicion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/telemetria/stream").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rutas", "/api/v1/rutas/**").permitAll()
                        .requestMatchers("/api/v1/demanda/**").permitAll()

                        // SCRUM-142: la ingesta la hace el equipo a bordo con su
                        // credencial propia. Ya no interviene ningun rol de persona.
                        .requestMatchers(HttpMethod.POST, "/api/v1/telemetria/posiciones")
                        .hasRole("EQUIPO")

                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Cierra por defecto: una ruta nueva sin regla explicita se
                        // rechaza en vez de quedar publicada por descuido.
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .addFilterBefore(equipoAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // ---- INICIO del bloque provisional. TODO(SCRUM-134): borrar entero ----
        // Concede ROLE_ADMIN por cabecera X-Admin-Token mientras no exista el
        // JwtAuthFilter de Firebase. Al borrarlo, los controladores no cambian:
        // siguen exigiendo hasRole('ADMIN') igual que hoy.
        http.addFilterBefore(adminBootstrapFilter, EquipoAuthFilter.class);
        // ---- FIN del bloque provisional ----

        return http.build();
    }

    /**
     * Sin origenes por ahora. SCRUM-274 llena {@code ecoruta.cors.origenes-permitidos}
     * por ambiente y aqui no hay nada mas que tocar.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties propiedades) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.origenesPermitidos());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    /**
     * Todo bean de tipo Filter lo recoge Boot y lo instala TAMBIEN en la cadena de
     * servlets, donde correria antes de Spring Security y en cada peticion. Estos
     * dos filtros solo deben vivir dentro de la cadena de seguridad.
     */
    @Bean
    public FilterRegistrationBean<EquipoAuthFilter> noRegistrarEquipoAuthFilter(
            EquipoAuthFilter filtro) {
        FilterRegistrationBean<EquipoAuthFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
