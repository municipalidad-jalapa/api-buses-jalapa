package gt.muni.jalapa.ecoruta.demanda;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param umbralSalida    umbral de "juntar N pasajeros" de otra historia; HU-57
 *                        no emite ningun aviso por este valor
 * @param ttlMinutos      vigencia de un registro de espera
 * @param geocercaMetros  radio para registrarse en una parada (otra historia)
 */
@ConfigurationProperties("ecoruta.demanda")
public record DemandaProperties(int umbralSalida, int ttlMinutos, int geocercaMetros) {

    public DemandaProperties {
        umbralSalida = umbralSalida <= 0 ? 10 : umbralSalida;
        ttlMinutos = ttlMinutos <= 0 ? 20 : ttlMinutos;
        geocercaMetros = geocercaMetros <= 0 ? 150 : geocercaMetros;
    }

    public Duration ttl() {
        return Duration.ofMinutes(ttlMinutos);
    }
}
