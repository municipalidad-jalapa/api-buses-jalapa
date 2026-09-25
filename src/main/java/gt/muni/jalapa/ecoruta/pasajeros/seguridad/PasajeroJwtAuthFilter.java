package gt.muni.jalapa.ecoruta.pasajeros.seguridad;

import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero;
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
 * Autentica al pasajero con su JWT (SCRUM-26, bloque B). Gemelo de los filtros
 * del conductor y del administrador: nunca lanza, y un token ausente, vencido o
 * de otro rol deja el contexto vacio.
 *
 * <p>El nombre del principal es el id del pasajero. El rol {@code ROLE_PASAJERO}
 * no abre ninguna ruta de operacion: en el panel del conductor o el municipal
 * recibe 403.
 */
@Component
@RequiredArgsConstructor
public class PasajeroJwtAuthFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_PASAJERO";

    private final EmisorDeJwt emisor;

    /**
     * Id de la cuenta del pasajero autenticado, o null si quien pide entra
     * como invitado o con otro rol. Lo usan los endpoints que funcionan con y
     * sin cuenta (reservas, opiniones).
     */
    public static Long pasajeroDe(org.springframework.security.core.Authentication autenticado) {
        if (autenticado == null || autenticado.getAuthorities().stream()
                .noneMatch(permiso -> ROL.equals(permiso.getAuthority()))) {
            return null;
        }
        return Long.valueOf(autenticado.getName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
            if (cabecera != null && cabecera.startsWith("Bearer ")) {
                String bearer = cabecera.substring("Bearer ".length()).trim();
                if (!bearer.isEmpty() && !bearer.startsWith("eq_")) {
                    emisor.leer(bearer, SesionDePasajero.ROL).ifPresent(sesion ->
                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            sesion.subject(), null, List.of(new SimpleGrantedAuthority(ROL)))));
                }
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
