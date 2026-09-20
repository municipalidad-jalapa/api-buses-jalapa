package gt.muni.jalapa.ecoruta.observabilidad;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Observabilidad E2E (SCRUM-19 / Devops-96): id de correlacion por peticion en
 * el MDC, para que ingesta, procesamiento y difusion compartan el mismo id.
 * Respeta X-Correlation-Id si viene; no vuelca cabeceras (nota de SCRUM-142).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CABECERA = "X-Correlation-Id";
    public static final String CLAVE_MDC = "correlationId";
    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String id = peticion.getHeader(CABECERA);
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(CLAVE_MDC, id);
        respuesta.setHeader(CABECERA, id);
        try { cadena.doFilter(peticion, respuesta); } finally { MDC.remove(CLAVE_MDC); }
    }
}
