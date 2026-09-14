package gt.muni.jalapa.ecoruta.precision.servicio;

import java.time.Duration;
import java.time.Instant;

/** Error absoluto en minutos entre la llegada predicha y la real (HU-73). */
public final class CalculadorDeError {

    private CalculadorDeError() {
    }

    public static double minutos(Instant predichoEn, int etaPredichoMin, Instant llegadaEn) {
        Instant esperada = predichoEn.plus(Duration.ofMinutes(etaPredichoMin));
        return Math.abs(Duration.between(esperada, llegadaEn).toMillis()) / 60_000.0;
    }
}
