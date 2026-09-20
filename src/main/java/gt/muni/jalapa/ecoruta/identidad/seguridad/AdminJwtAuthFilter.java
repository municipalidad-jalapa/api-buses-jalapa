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
 * Autentica al administrador municipal con el JWT propio del backend
 * (SCRUM-173). Gemelo de {@link ConductorJwtAuthFilter}: nunca lanza, y un
 * token ausente, invalido, vencido o de otro rol deja el contexto vacio.
 */
@Component
@RequiredArgsConstructor
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_ADMIN";

    private final EmisorDeJwt emisor;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            String bearer = cabecera.substring("Bearer ".length()).trim();
            if (!bearer.isEmpty() && !bearer.startsWith("eq_")) {
                emisor.leer(bearer, EmisorDeJwt.ROL_ADMIN).ifPresent(sesion ->
                        SecurityContextHolder.getContext().setAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated(
                                        sesion.subject(), null, List.of(new SimpleGrantedAuthority(ROL)))));
            }
        }

        cadena.doFilter(peticion, respuesta);
    }
}
