package gt.muni.jalapa.ecoruta.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Responde 401 con el mismo formato ApiError que el resto de la API (SCRUM-114).
 *
 * <p>Hace falta porque el @RestControllerAdvice vive dentro del DispatcherServlet
 * y una credencial rechazada se decide en el ExceptionTranslationFilter, aguas
 * arriba: el handler global no llega a verla. Sin esto el 401 saldria con el
 * cuerpo por defecto de Boot, que no trae la clave "message".
 */
@Component
@RequiredArgsConstructor
public class ApiErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * El ObjectMapper de Boot, no uno nuevo: uno nuevo serializaria el Instant
     * como numero de epoca y romperia en silencio la paridad con el handler.
     */
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta,
                         AuthenticationException excepcion) throws IOException {
        // Mensaje FIJO. Aqui no se interpola nada de la peticion: la cabecera
        // Authorization lleva el secreto y no puede acabar en el cuerpo (criterio
        // (d) de SCRUM-142).
        ApiError error = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Credencial ausente o invalida",
                peticion.getRequestURI());

        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(respuesta.getOutputStream(), error);
    }
}
