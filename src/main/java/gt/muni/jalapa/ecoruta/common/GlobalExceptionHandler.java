package gt.muni.jalapa.ecoruta.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                req
        );
    }

    /**
     * Credenciales incorrectas.
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

    /**
     * Errores producidos por validaciones con
     * anotaciones como @Valid.
     */
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

    /**
     * HU-85.
     *
     * Las validaciones del historico lanzan
     * IllegalArgumentException cuando los valores
     * recibidos no son validos.
     *
     * Ejemplos:
     * - horaInicio = 25
     * - horaInicio >= horaFin
     * - vehiculoId = 0
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(
            IllegalArgumentException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                req
        );
    }

    /**
     * HU-85.
     *
     * Parametro obligatorio ausente.
     *
     * Ejemplo:
     * GET /historico/demanda?fecha=2026-09-17
     *
     * sin paradaId.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest req
    ) {

        String mensaje =
                "Falta el parametro obligatorio: "
                        + ex.getParameterName();

        return build(
                HttpStatus.BAD_REQUEST,
                mensaje,
                req
        );
    }

    /**
     * Parametro presente pero con formato incorrecto.
     *
     * Ejemplo:
     * fecha=esto-no-es-una-fecha
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest req
    ) {

        String mensaje =
                "Valor invalido para el parametro: "
                        + ex.getName();

        return build(
                HttpStatus.BAD_REQUEST,
                mensaje,
                req
        );
    }

    /**
     * Construye el formato uniforme ApiError.
     */
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