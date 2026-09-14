package gt.muni.jalapa.ecoruta.eta;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros del calculo de ETA (SCRUM-166, HU Desarrollo-71).
 *
 * @param velocidadRespaldoKmh             velocidad promedio del recorrido cuando
 *                                         no hay historial suficiente. PROVISIONAL
 *                                         hasta tener la cifra del estudio de campo
 * @param velocidadMinimaKmh               lecturas por debajo son el bus detenido
 *                                         y no cuentan como velocidad observada
 * @param muestrasVelocidad                cuantas lecturas recientes se promedian
 * @param ventanaVelocidadMinutos          que tan atras se buscan esas lecturas,
 *                                         contado desde la ultima posicion
 * @param antiguedadMaximaSegundos         con una posicion mas vieja el ETA se
 *                                         marca como no disponible
 * @param intervaloMinimoRecalculoSegundos limite de frecuencia del recalculo que
 *                                         dispara la llegada de posiciones
 */
@ConfigurationProperties("ecoruta.eta")
public record EtaProperties(double velocidadRespaldoKmh, double velocidadMinimaKmh,
                            int muestrasVelocidad, int ventanaVelocidadMinutos,
                            int antiguedadMaximaSegundos, int intervaloMinimoRecalculoSegundos) {

    public EtaProperties {
        velocidadRespaldoKmh = velocidadRespaldoKmh <= 0 ? 20 : velocidadRespaldoKmh;
        velocidadMinimaKmh = velocidadMinimaKmh <= 0 ? 5 : velocidadMinimaKmh;
        muestrasVelocidad = muestrasVelocidad <= 0 ? 5 : muestrasVelocidad;
        ventanaVelocidadMinutos = ventanaVelocidadMinutos <= 0 ? 3 : ventanaVelocidadMinutos;
        antiguedadMaximaSegundos = antiguedadMaximaSegundos <= 0 ? 120 : antiguedadMaximaSegundos;
        intervaloMinimoRecalculoSegundos = intervaloMinimoRecalculoSegundos <= 0
                ? 10 : intervaloMinimoRecalculoSegundos;
    }

    public Duration ventanaVelocidad() {
        return Duration.ofMinutes(ventanaVelocidadMinutos);
    }

    public Duration antiguedadMaxima() {
        return Duration.ofSeconds(antiguedadMaximaSegundos);
    }

    public Duration intervaloMinimoRecalculo() {
        return Duration.ofSeconds(intervaloMinimoRecalculoSegundos);
    }
}
