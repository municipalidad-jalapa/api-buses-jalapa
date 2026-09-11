package gt.muni.jalapa.ecoruta.identidad.seguridad;

import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica al conductor con el JWT propio del backend.
 *
 * <p>Igual que {@code EquipoAuthFilter}: nunca lanza. Token ausente, mal
 * firmado, vencido o sin rol de conductor deja el contexto vacio; responde
 * la capa de autorizacion con el {@code ApiError} de 401.
 */
@Component
@RequiredArgsConstructor
public class ConductorJwtAuthFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_CONDUCTOR";

    private final EmisorDeJwt emisor;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        String bearer = extraerBearer(peticion.getHeader(HttpHeaders.AUTHORIZATION));
        if (bearer == null || bearer.startsWith("eq_")) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        emisor.leerConductor(bearer).ifPresent(sesion -> {
            var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                    sesion.subject(),
                    null,
                    List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        });

        cadena.doFilter(peticion, respuesta);
    }

    private static String extraerBearer(String cabecera) {
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            return null;
        }
        String valor = cabecera.substring("Bearer ".length()).trim();
        return valor.isEmpty() ? null : valor;
    }
}
