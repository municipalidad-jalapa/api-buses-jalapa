package gt.muni.jalapa.ecoruta.demanda.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros de la demanda en parada.
 *
 * <p>La vigencia fija la expiracion al crear la reserva (SCRUM-306 y Desarrollo-135).
 * {@code geocerca-metros} es el radio anti-abuso (ADR-002 / ADR-007).
 *
 * <p>{@code ritmo-minimo-segundos} es el otro anti-abuso (HU Desarrollo-95): el
 * tiempo minimo que debe pasar entre dos reservas creadas por el mismo
 * dispositivo, sin importar que la anterior ya se haya cancelado o vencido. Sin
 * esto, la geocerca y el indice de "una vigente por dispositivo" no impiden que
 * un dispositivo cree y cancele reservas en bucle para inflar el conteo
 * historico de demanda: nunca hay mas de una vigente a la vez, pero el total de
 * registros crece sin limite.
 */
@ConfigurationProperties("ecoruta.demanda")
public record DemandaProperties(
        Integer vigenciaMinutos, 
        Integer barridoSegundos,
        int umbralSalida, 
        int geocercaMetros, 
        int ritmoMinimoSegundos) {

    public DemandaProperties {
        vigenciaMinutos = (vigenciaMinutos == null || vigenciaMinutos <= 0) ? 5 : vigenciaMinutos;
        barridoSegundos = (barridoSegundos == null || barridoSegundos <= 0) ? 60 : barridoSegundos;
        umbralSalida = umbralSalida <= 0 ? 10 : umbralSalida;
        geocercaMetros = geocercaMetros <= 0 ? 150 : geocercaMetros;
        ritmoMinimoSegundos = ritmoMinimoSegundos <= 0 ? 5 : ritmoMinimoSegundos;
    }

    public Duration vigencia() {
        return Duration.ofMinutes(vigenciaMinutos);
    }

    public Duration ritmoMinimo() {
        return Duration.ofSeconds(ritmoMinimoSegundos);
    }
}