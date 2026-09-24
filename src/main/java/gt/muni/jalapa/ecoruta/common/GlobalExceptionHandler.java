package gt.muni.jalapa.ecoruta.common;

import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * El stream de posiciones (SSE) vence por diseno cada
     * {@code sse-timeout-minutos} y el cliente reconecta solo. No es un error:
     * sin este manejador, Spring dejaba dos WARN por cliente en cada cierre
     * (QA, ronda 2). La respuesta ya esta enviada, asi que no se escribe nada.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void streamVencido() {
        // Cierre normal del SSE.
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> notFound(
            RecursoNoEncontradoException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                req
        );
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    public ResponseEntity<ApiError> unprocessable(
            ReglaDeNegocioException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                req
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiError> forbidden(
        AccessDeniedException ex,
        HttpServletRequest req) {

    return build(
            HttpStatus.FORBIDDEN,
            ex.getMessage(),
            req
    );
}

    /**
     * Sin esto la BadCredentialsException escapa del controller
     * y Spring Security puede responder 403 sin cuerpo util.
     * El contrato es 401 con formato ApiError.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> unauthorized(
            BadCredentialsException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                req
        );
    }

    /**
     * HU-76.
     * El conductor intento operar sobre una ruta
     * que no le pertenece.
     */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ApiError> forbidden(
            AccesoDenegadoException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                req
        );
    }

    /**
     * HU-76.
     * La parada ya fue marcada como atendida.
     */
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflict(
            ConflictoException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                req
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> badRequest(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        String detalle =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(f ->
                                f.getField()
                                        + ": "
                                        + f.getDefaultMessage()
                        )
                        .findFirst()
                        .orElse("Solicitud invalida");

        return build(
                HttpStatus.BAD_REQUEST,
                detalle,
                req
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            HttpServletRequest req
    ) {
        return ResponseEntity
                .status(status)
                .body(
                        ApiError.of(
                                status.value(),
                                status.getReasonPhrase(),
                                message,
                                req.getRequestURI()
                        )
                );
    }
}
