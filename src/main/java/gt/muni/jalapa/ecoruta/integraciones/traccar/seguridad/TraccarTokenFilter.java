package gt.muni.jalapa.ecoruta.integraciones.traccar.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Autentica al servicio Traccar con {@code X-Traccar-Token} (SCRUM-24).
 *
 * <p>Vive solo en la cadena de seguridad de la integracion: el token de Traccar
 * no abre ninguna otra ruta, y el {@code Bearer eq_...} del equipo a bordo ni
 * se evalua aqui. Las dos credenciales no se intercambian.
 *
 * <p>Nunca lanza: sin cabecera o con un token distinto deja el contexto vacio y
 * la autorizacion responde 401. La comparacion es en tiempo constante.
 */
public class TraccarTokenFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Traccar-Token";
    public static final String ROL = "ROLE_INTEGRACION_TRACCAR";

    /** Null si no hay token configurado: no se concede nada. */
    private final byte[] tokenEsperado;

    public TraccarTokenFilter(String tokenEsperado) {
        this.tokenEsperado = tokenEsperado == null || tokenEsperado.isBlank()
                ? null
                : tokenEsperado.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String presentado = peticion.getHeader(CABECERA);
        if (tokenEsperado != null && presentado != null
                && MessageDigest.isEqual(presentado.getBytes(StandardCharsets.UTF_8), tokenEsperado)) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            "traccar", null, List.of(new SimpleGrantedAuthority(ROL))));
        }
        cadena.doFilter(peticion, respuesta);
    }
}
