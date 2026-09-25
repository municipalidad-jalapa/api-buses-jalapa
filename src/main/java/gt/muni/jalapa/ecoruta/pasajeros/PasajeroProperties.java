package gt.muni.jalapa.ecoruta.pasajeros;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Sesion opcional del pasajero (SCRUM-26, bloque B).
 *
 * @param sesionDias cuanto dura la sesion del pasajero antes de pedir entrar de
 *                   nuevo con Google. Larga a proposito: el pasajero no opera
 *                   nada sensible y la eleccion se recuerda entre visitas
 */
@ConfigurationProperties("ecoruta.pasajero")
public record PasajeroProperties(int sesionDias) {

    public PasajeroProperties {
        sesionDias = sesionDias <= 0 ? 30 : sesionDias;
    }

    public Duration sesion() {
        return Duration.ofDays(sesionDias);
    }
}
