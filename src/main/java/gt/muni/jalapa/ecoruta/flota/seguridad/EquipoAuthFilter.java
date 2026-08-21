package gt.muni.jalapa.ecoruta.flota.seguridad;

import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
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
import java.util.Optional;

/**
 * Autentica al equipo a bordo por su credencial propia (SCRUM-142).
 *
 * <p>El filtro NUNCA lanza. Un token ausente, mal formado, desconocido o revocado
 * deja el SecurityContext vacio y sigue la cadena; quien responde es la capa de
 * autorizacion. Asi lo documenta la coleccion de Postman ("ignora el token malo y
 * deja pasar la peticion sin autenticacion; Security decide") y por eso sus casos
 * 3.1 y 3.7 aceptan 401 o 403 indistintamente.
 */
@Component
@RequiredArgsConstructor
public class EquipoAuthFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_EQUIPO";

    private final EquipoService equipoService;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        // Si otro filtro ya autentico, no se toca nada. Es lo que permitira que el
        // JwtAuthFilter de Firebase (SCRUM-134) conviva con este sin coordinarse.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        Optional<TokenDeEquipo> token = TokenDeEquipo.parsear(
                peticion.getHeader(HttpHeaders.AUTHORIZATION));

        // Vacio no significa "credencial invalida": puede ser un bearer de otro.
        // Se deja intacto para que lo vea quien corresponda.
        if (token.isEmpty()) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        equipoService.autenticar(token.get()).ifPresent(equipo -> {
            var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                    equipo,
                    // Credenciales en null a proposito: nada sensible entra al
                    // SecurityContext.
                    null,
                    List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        });

        cadena.doFilter(peticion, respuesta);
    }
}
