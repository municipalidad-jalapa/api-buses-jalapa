package gt.muni.jalapa.ecoruta.aceptacion;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Captura lo que la aplicacion escribe en el registro durante un escenario, para
 * poder afirmar sobre ello.
 *
 * <p>No se usa {@code OutputCaptureExtension} de Spring Boot porque es una
 * extension de JUnit y Cucumber no ejecuta ese ciclo de vida. Un
 * {@code ListAppender} de Logback enchufado al logger raiz hace lo mismo y
 * funciona en los dos mundos.
 */
@Component
public class RegistroDelEscenario {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public void empezarACapturar() {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.list.clear();
        appender.setContext(raiz.getLoggerContext());
        appender.start();
        raiz.addAppender(appender);
    }

    public void dejarDeCapturar() {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        raiz.detachAppender(appender);
        appender.stop();
    }

    /** Todo lo registrado en el escenario, con los parametros ya interpolados. */
    public String texto() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }
}
