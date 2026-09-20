package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Limite de peticiones por IP y por dispositivo en los endpoints publicos
 * (HU Desarrollo-95, criterio "límite de peticiones por dispositivo y por IP").
 *
 * <p>Solo protege las rutas publicas listadas en {@link #RUTAS_PROTEGIDAS}: son
 * las mismas que {@code SecurityConfig} deja en {@code permitAll()}. Se
 * mantienen separadas a proposito — agregar una ruta aqui no la hace publica
 * (eso lo decide SecurityConfig) — asi que si SecurityConfig abre una ruta
 * publica nueva, esta lista tiene que actualizarse a mano. Si se olvida,
 * {@code RutasPublicasProtegidasIT} falla nombrando el endpoint que falta
 * (asi se le escapo a QA {@code POST /api/v1/auth/admin}).
 *
 * <p>Corre antes de la autenticacion (ver el orden en {@code SecurityConfig}):
 * una peticion que ya viene mal de ritmo no necesita gastar trabajo de
 * autenticacion o de base de datos.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";

    private record RutaProtegida(String metodo, String patron) {
    }

    private static final List<RutaProtegida> RUTAS_PROTEGIDAS = List.of(
            new RutaProtegida("GET", "/api/v1/rutas"),
            new RutaProtegida("GET", "/api/v1/rutas/**"),
            new RutaProtegida("GET", "/api/v1/telemetria/posicion"),
            new RutaProtegida("GET", "/api/v1/telemetria/stream"),
            new RutaProtegida("POST", "/api/v1/reservas"),
            new RutaProtegida("POST", "/api/v1/reservas/*/renovacion"),
            new RutaProtegida("DELETE", "/api/v1/reservas/*"),
            // HU-76: el pasajero consulta el estado de una de sus reservas.
            new RutaProtegida("GET", "/api/v1/reservas/*"),
            // HU-76: el pasajero declara que no logro abordar.
            new RutaProtegida("POST", "/api/v1/reservas/*/declaracion-no-abordo"),
            new RutaProtegida("POST", "/api/v1/reservas/*/abordaje"),
            new RutaProtegida("POST", "/api/v1/dispositivos/notificaciones"),
            new RutaProtegida("POST", "/api/v1/auth/conductor"),
            // SCRUM-173: login del panel municipal. Cada peticion valida un
            // idToken contra Firebase: sin limite permite probar tokens en masa.
            new RutaProtegida("POST", "/api/v1/auth/admin"));

    private final AntPathMatcher patrones = new AntPathMatcher();
    private final RateLimitProperties propiedades;

    /**
     * El ObjectMapper de Boot, no uno nuevo: uno nuevo serializaria el Instant
     * de ApiError como numero de epoca (mismo motivo que ApiErrorAuthenticationEntryPoint).
     */
    private final ObjectMapper objectMapper;

    private final LimitadorDeVentanaFija porIp;
    private final LimitadorDeVentanaFija porDispositivo;

    public RateLimitFilter(RateLimitProperties propiedades, ObjectMapper objectMapper, Clock reloj) {
        this.propiedades = propiedades;
        this.objectMapper = objectMapper;
        this.porIp = new LimitadorDeVentanaFija(
                propiedades.porIpCapacidad(), propiedades.porIpVentanaSegundos(), reloj);
        this.porDispositivo = new LimitadorDeVentanaFija(
                propiedades.porDispositivoCapacidad(), propiedades.porDispositivoVentanaSegundos(), reloj);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (!propiedades.habilitado() || !esRutaProtegida(peticion)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        LimitadorDeVentanaFija.Resultado resultadoIp = porIp.intentar(peticion.getRemoteAddr());
        if (!resultadoIp.permitido()) {
            responderDemasiadasPeticiones(peticion, respuesta, resultadoIp.segundosParaReintentar(),
                    "Demasiadas solicitudes desde esta IP. Intenta de nuevo más tarde.");
            return;
        }

        String dispositivoId = peticion.getHeader(CABECERA_DISPOSITIVO);
        if (StringUtils.hasText(dispositivoId)) {
            LimitadorDeVentanaFija.Resultado resultadoDispositivo = porDispositivo.intentar(dispositivoId);
            if (!resultadoDispositivo.permitido()) {
                responderDemasiadasPeticiones(peticion, respuesta, resultadoDispositivo.segundosParaReintentar(),
                        "Demasiadas solicitudes desde este dispositivo. Intenta de nuevo más tarde.");
                return;
            }
        }

        cadena.doFilter(peticion, respuesta);
    }

    /** Limpieza periodica: la llama {@link LimpiadorDeLimitadores}. */
    void limpiar() {
        porIp.limpiar();
        porDispositivo.limpiar();
    }

    /** Visible para RutasPublicasProtegidasIT, que la contrasta con la autorizacion real. */
    boolean esRutaProtegida(HttpServletRequest peticion) {
        String metodo = peticion.getMethod();
        String ruta = peticion.getRequestURI();
        return RUTAS_PROTEGIDAS.stream().anyMatch(
                r -> r.metodo().equalsIgnoreCase(metodo) && patrones.match(r.patron(), ruta));
    }

    private void responderDemasiadasPeticiones(HttpServletRequest peticion, HttpServletResponse respuesta,
                                               long segundosParaReintentar, String mensaje) throws IOException {

        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setHeader("Retry-After", String.valueOf(segundosParaReintentar));
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                mensaje,
                peticion.getRequestURI());
        objectMapper.writeValue(respuesta.getOutputStream(), error);
    }
}
