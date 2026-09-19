package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Complementa al GlobalExceptionHandler de SCRUM-114 sin tocar ese archivo, que
 * se mantiene byte-identico a su rama para que no de conflicto al mergearse.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorDeErroresWeb {

    /**
     * Cuerpo ilegible: JSON mal formado, o un timestamp que no es un Instant
     * valido. Es muy probable viniendo de un equipo de campo.
     *
     * <p>El mensaje se sustituye por uno generico a proposito: el de Jackson puede
     * incluir un fragmento del cuerpo recibido, y ese cuerpo va a parar tal cual a
     * ApiError.message.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException excepcion,
                                                   HttpServletRequest peticion) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Cuerpo de la peticion malformado o ilegible",
                peticion.getRequestURI()));
    }

    /**
     * Identificador de ruta con formato invalido en la URL, p. ej.
     * {@code /api/v1/rutas/abc/resumen}. Sin esto Spring responde 400 pero con el
     * cuerpo generico de {@code /error}, no con ApiError (SCRUM-284).
     *
     * <p>Solo se nombra el parametro, nunca el valor recibido: iria tal cual a
     * {@code ApiError.message}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> identificadorInvalido(MethodArgumentTypeMismatchException excepcion,
                                                          HttpServletRequest peticion) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "El parametro '" + excepcion.getName() + "' tiene un formato invalido",
                peticion.getRequestURI()));
    }
}
