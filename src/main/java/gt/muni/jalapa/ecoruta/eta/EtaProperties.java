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
 * @param esperaParadaSegundos             tiempo que el bus se detiene en cada
 *                                         parada intermedia sin reservas
 * @param esperaConReservaSegundos         tiempo detenido en una parada con
 *                                         reservas activas (sube gente)
 * @param radioParadaMetros                a esta distancia o menos el bus esta
 *                                         "en la parada"
 * @param detenidoMaximoMinutos            detenido fuera de parada por mas tiempo
 *                                         (averia, fin de turno): sin estimacion
 * @param desvioMetros                     a mas de esta distancia del trazado el
 *                                         bus se considera en desvio
 * @param factorDesvio                     cuanto mas larga que la linea recta es,
 *                                         en promedio, la vuelta por calles para
 *                                         reincorporarse al trazado
 */
@ConfigurationProperties("ecoruta.eta")
public record EtaProperties(double velocidadRespaldoKmh, double velocidadMinimaKmh,
                            int muestrasVelocidad, int ventanaVelocidadMinutos,
                            int antiguedadMaximaSegundos, int intervaloMinimoRecalculoSegundos,
                            int esperaParadaSegundos, int esperaConReservaSegundos,
                            int radioParadaMetros, int detenidoMaximoMinutos,
                            int desvioMetros, double factorDesvio) {

    public EtaProperties {
        velocidadRespaldoKmh = velocidadRespaldoKmh <= 0 ? 20 : velocidadRespaldoKmh;
        velocidadMinimaKmh = velocidadMinimaKmh <= 0 ? 5 : velocidadMinimaKmh;
        muestrasVelocidad = muestrasVelocidad <= 0 ? 5 : muestrasVelocidad;
        ventanaVelocidadMinutos = ventanaVelocidadMinutos <= 0 ? 3 : ventanaVelocidadMinutos;
        antiguedadMaximaSegundos = antiguedadMaximaSegundos <= 0 ? 120 : antiguedadMaximaSegundos;
        intervaloMinimoRecalculoSegundos = intervaloMinimoRecalculoSegundos <= 0
                ? 10 : intervaloMinimoRecalculoSegundos;
        esperaParadaSegundos = esperaParadaSegundos < 0 ? 5 : esperaParadaSegundos;
        esperaConReservaSegundos = esperaConReservaSegundos <= 0 ? 20 : esperaConReservaSegundos;
        radioParadaMetros = radioParadaMetros <= 0 ? 40 : radioParadaMetros;
        detenidoMaximoMinutos = detenidoMaximoMinutos <= 0 ? 5 : detenidoMaximoMinutos;
        desvioMetros = desvioMetros <= 0 ? 60 : desvioMetros;
        factorDesvio = factorDesvio < 1 ? 1.3 : factorDesvio;
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

    public Duration detenidoMaximo() {
        return Duration.ofMinutes(detenidoMaximoMinutos);
    }
}
