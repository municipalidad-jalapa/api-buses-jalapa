package gt.muni.jalapa.ecoruta.seguridad.bootstrap;

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
 * PROVISIONAL — TODO(SCRUM-134).
 *
 * <p>Concede ROLE_ADMIN a quien presente el token de arranque en la cabecera
 * {@code X-Admin-Token}. Existe solo porque los endpoints de emision y
 * revocacion de credenciales necesitan proteccion HOY, y la autenticacion de
 * personas (Firebase, SCRUM-134) todavia no esta construida.
 *
 * <p>Va en cabecera propia y no en Authorization a proposito: tres consumidores
 * de bearer en una sola cabecera -- equipo, admin y el futuro JWT de Firebase --
 * es un problema de coordinacion donde cada filtro tendria que conocer el formato
 * de los otros. Con cabecera aparte, borrar este paquete no puede afectar en nada
 * al camino real de autenticacion.
 *
 * <p>Sin token configurado el filtro queda inerte y nadie obtiene ROLE_ADMIN, asi
 * que /api/v1/admin/** rechaza a todos: falla cerrado. El bean existe igual, en
 * vez de no definirse, porque un @Bean que devuelve null deja un NullBean que
 * rompe a quien recolecte los Filter por tipo (empezando por el MockMvc de las
 * pruebas).
 *
 * <p><b>Para borrarlo cuando aterrice SCRUM-134:</b> borrar este paquete entero;
 * quitar el bloque marcado en SecurityConfig; quitar {@code ecoruta.admin} de
 * application.yml y ECORUTA_ADMIN_TOKEN de docker-compose.yml; borrar sus
 * pruebas. Los controladores NO cambian: siguen exigiendo hasRole('ADMIN').
 */
public class AdminBootstrapFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Admin-Token";

    /** Null cuando no hay token configurado: el filtro no concede nada. */
    private final byte[] tokenEsperado;

    public AdminBootstrapFilter(String tokenEsperado) {
        this.tokenEsperado = tokenEsperado == null
                ? null
                : tokenEsperado.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (tokenEsperado != null && concedeAdmin(peticion.getHeader(CABECERA))) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            "admin-bootstrap", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }

        // Igual que el filtro del equipo: no lanza nunca, decide la autorizacion.
        cadena.doFilter(peticion, respuesta);
    }

    private boolean concedeAdmin(String presentado) {
        return presentado != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && MessageDigest.isEqual(
                        presentado.getBytes(StandardCharsets.UTF_8), tokenEsperado);
    }
}
