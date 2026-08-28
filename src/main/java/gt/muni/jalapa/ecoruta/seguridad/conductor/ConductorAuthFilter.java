package gt.muni.jalapa.ecoruta.seguridad.conductor;

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
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link Usuario}.
 *
 * <p>Concede {@code ROLE_CONDUCTOR} a quien presente un JWT firmado por
 * {@link ConductorJwtService}. El filtro NUNCA lanza ni responde: token
 * ausente, mal formado o vencido deja el SecurityContext intacto y sigue
 * la cadena; quien responde es la capa de autorizacion.
 *
 * <p>Si ya hay {@code Authentication}, no se toca nada. Es lo que permitira
 * que el JwtAuthFilter de Firebase conviva con este sin coordinarse, igual
 * que {@code EquipoAuthFilter}.
 *
 * <p>No hay colision con el token de equipo: ese exige prefijo {@code eq_}
 * y un JWT no lo tiene. Lo que no verifica este servicio se deja pasar
 * intacto para que lo vea quien corresponda.
 */
@Component
@RequiredArgsConstructor
public class ConductorAuthFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_CONDUCTOR";

    private static final String PREFIJO_BEARER = "Bearer ";

    private final ConductorJwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        String token = bearerDe(peticion.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        jwtService.validarYObtenerUsername(token).ifPresent(username -> {
            var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                    username,
                    null,
                    List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        });

        cadena.doFilter(peticion, respuesta);
    }

    /**
     * Extrae el valor Bearer. Vacio no es "token invalido": puede ser un
     * bearer de otro (equipo {@code eq_}, futuro Firebase) y tiene que
     * seguir su camino intacto.
     */
    private static String bearerDe(String cabeceraAuthorization) {
        if (cabeceraAuthorization == null || !cabeceraAuthorization.startsWith(PREFIJO_BEARER)) {
            return null;
        }
        String valor = cabeceraAuthorization.substring(PREFIJO_BEARER.length()).trim();
        return valor.isEmpty() ? null : valor;
    }
}
