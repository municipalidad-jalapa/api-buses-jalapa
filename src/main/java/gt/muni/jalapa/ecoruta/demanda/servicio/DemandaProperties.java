package gt.muni.jalapa.ecoruta.demanda.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros del modulo demanda (Desarrollo-135).
 *
 * @param vigenciaMinutos cuanto dura una reserva desde que se crea o se renueva
 *                        hasta que vence. Configurable; cinco minutos por defecto
 * @param barridoSegundos cada cuanto la tarea programada recorre las reservas
 *                        vencidas y las marca EXPIRADA
 */
@ConfigurationProperties("ecoruta.demanda")
public record DemandaProperties(Integer vigenciaMinutos, Integer barridoSegundos) {

    public DemandaProperties {
        vigenciaMinutos = (vigenciaMinutos == null || vigenciaMinutos <= 0) ? 5 : vigenciaMinutos;
        barridoSegundos = (barridoSegundos == null || barridoSegundos <= 0) ? 60 : barridoSegundos;
    }

    public Duration vigencia() {
        return Duration.ofMinutes(vigenciaMinutos);
    }
}
