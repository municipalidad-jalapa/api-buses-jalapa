package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.seguridad.RutasPublicas;
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

/**
 * Limite de peticiones por IP y por dispositivo en los endpoints publicos
 * (HU Desarrollo-95, criterio "límite de peticiones por dispositivo y por IP").
 *
 * <p>Las rutas que limita salen de {@link RutasPublicas}, el mismo catalogo con
 * el que las cadenas de seguridad las abren: no hay una segunda lista que
 * mantener a mano (SCRUM-26, correccion de QA). Las de cupo
 * {@link RutasPublicas.Cupo#ESTRICTO} tienen ademas un limite por IP propio,
 * mucho menor, contado aparte por ruta.
 *
 * <p>Corre antes de la autenticacion (ver el orden en {@code SecurityConfig}):
 * una peticion que ya viene mal de ritmo no necesita gastar trabajo de
 * autenticacion o de base de datos.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";

    private final AntPathMatcher patrones = new AntPathMatcher();
    private final RateLimitProperties propiedades;

    /**
     * El ObjectMapper de Boot, no uno nuevo: uno nuevo serializaria el Instant
     * de ApiError como numero de epoca (mismo motivo que ApiErrorAuthenticationEntryPoint).
     */
    private final ObjectMapper objectMapper;

    private final LimitadorDeVentanaFija porIp;
    private final LimitadorDeVentanaFija porDispositivo;
    private final LimitadorDeVentanaFija porIpEstricto;

    public RateLimitFilter(RateLimitProperties propiedades, ObjectMapper objectMapper, Clock reloj) {
        this.propiedades = propiedades;
        this.objectMapper = objectMapper;
        this.porIp = new LimitadorDeVentanaFija(
                propiedades.porIpCapacidad(), propiedades.porIpVentanaSegundos(), reloj);
        this.porDispositivo = new LimitadorDeVentanaFija(
                propiedades.porDispositivoCapacidad(), propiedades.porDispositivoVentanaSegundos(), reloj);
        this.porIpEstricto = new LimitadorDeVentanaFija(
                propiedades.porIpEstrictoCapacidad(), propiedades.porIpEstrictoVentanaSegundos(), reloj);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        RutasPublicas.Ruta ruta = propiedades.habilitado() ? rutaProtegida(peticion) : null;
        if (ruta == null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        String ip = peticion.getRemoteAddr();
        LimitadorDeVentanaFija.Resultado resultadoIp = porIp.intentar(ip);
        if (!resultadoIp.permitido()) {
            responderDemasiadasPeticiones(peticion, respuesta, resultadoIp.segundosParaReintentar(),
                    "Demasiadas solicitudes desde esta IP. Intenta de nuevo más tarde.");
            return;
        }

        if (ruta.cupo() == RutasPublicas.Cupo.ESTRICTO) {
            // Por ruta: agotar las opiniones no debe impedir iniciar sesion.
            LimitadorDeVentanaFija.Resultado resultadoEstricto = porIpEstricto.intentar(ip + " " + ruta.patron());
            if (!resultadoEstricto.permitido()) {
                responderDemasiadasPeticiones(peticion, respuesta, resultadoEstricto.segundosParaReintentar(),
                        "Demasiadas solicitudes desde esta IP. Intenta de nuevo más tarde.");
                return;
            }
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
        porIpEstricto.limpiar();
    }

    /** Olvida todo lo contado. Solo para aislar las pruebas entre si. */
    public void reiniciar() {
        porIp.reiniciar();
        porDispositivo.reiniciar();
        porIpEstricto.reiniciar();
    }

    /** Visible para RutasPublicasProtegidasIT, que la contrasta con la autorizacion real. */
    boolean esRutaProtegida(HttpServletRequest peticion) {
        return rutaProtegida(peticion) != null;
    }

    /** La primera ruta del catalogo que coincide, o null si la peticion no se limita. */
    private RutasPublicas.Ruta rutaProtegida(HttpServletRequest peticion) {
        String metodo = peticion.getMethod();
        String uri = peticion.getRequestURI();
        return RutasPublicas.TODAS.stream()
                .filter(r -> (r.metodo() == null || r.metodo().matches(metodo)) && patrones.match(r.patron(), uri))
                .findFirst()
                .orElse(null);
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
