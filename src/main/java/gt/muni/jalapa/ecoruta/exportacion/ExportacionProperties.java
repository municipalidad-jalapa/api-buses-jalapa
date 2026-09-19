package gt.muni.jalapa.ecoruta.exportacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Exportacion de datos del servicio para la Municipalidad (HU Desarrollo-86).
 *
 * @param zonaHoraria         zona en la que se interpretan las fechas del rango y en
 *                            la que se agrupan los dias del reporte. La base guarda
 *                            todo en UTC, pero "el 5 de septiembre" para la
 *                            Municipalidad es el dia de Guatemala
 * @param rangoMaximoDias     tope de dias por exportacion (inclusive), para que una
 *                            consulta sin cuidado no barra toda la tabla de posiciones
 * @param saltoMaximoSegundos entre dos lecturas GPS seguidas del mismo bus, un
 *                            intervalo mayor es un hueco (equipo apagado, sin
 *                            cobertura) y no cuenta como distancia recorrida: la
 *                            linea recta que une los dos puntos no es el camino
 */
@ConfigurationProperties("ecoruta.exportacion")
public record ExportacionProperties(String zonaHoraria, int rangoMaximoDias, int saltoMaximoSegundos) {

    public ExportacionProperties {
        zonaHoraria = StringUtils.hasText(zonaHoraria) ? zonaHoraria : "America/Guatemala";
        rangoMaximoDias = rangoMaximoDias <= 0 ? 366 : rangoMaximoDias;
        saltoMaximoSegundos = saltoMaximoSegundos <= 0 ? 300 : saltoMaximoSegundos;
        try {
            ZoneId.of(zonaHoraria);
        } catch (DateTimeException e) {
            // Falla al arrancar y no en la primera exportacion.
            throw new IllegalArgumentException(
                    "ecoruta.exportacion.zona-horaria no es una zona valida: " + zonaHoraria, e);
        }
    }

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    public Duration saltoMaximo() {
        return Duration.ofSeconds(saltoMaximoSegundos);
    }
}
