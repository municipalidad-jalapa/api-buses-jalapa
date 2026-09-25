package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de peticiones por clave (IP o dispositivo) con ventana fija en
 * memoria (HU Desarrollo-95).
 *
 * <p>Ventana fija, no "sliding window": mas simple, y el peor caso (un pico
 * justo en el borde de dos ventanas dejando pasar hasta el doble de la
 * capacidad durante un instante) es aceptable para la carga de este proyecto,
 * "decenas de pasajeros concurrentes en hora pico" (ADR-009). Un limitador mas
 * preciso no cambia la conclusion de negocio y si el costo de mantenerlo.
 *
 * <p>Sin dependencias externas: un {@code ConcurrentHashMap} por instancia.
 * {@link #limpiar()} descarta las claves ya inactivas para que la memoria no
 * crezca sin limite con IPs y dispositivos que no vuelven a aparecer.
 */
public final class LimitadorDeVentanaFija {

    /** Resultado de un intento: si se permite, y cuanto falta para el proximo reinicio. */
    public record Resultado(boolean permitido, long segundosParaReintentar) {
    }

    private record Ventana(long inicioEpochSegundos, int conteo) {
    }

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final int capacidad;
    private final long duracionSegundos;
    private final Clock reloj;

    public LimitadorDeVentanaFija(int capacidad, long duracionSegundos, Clock reloj) {
        this.capacidad = capacidad;
        this.duracionSegundos = duracionSegundos;
        this.reloj = reloj;
    }

    public Resultado intentar(String clave) {
        long ahora = Instant.now(reloj).getEpochSecond();
        Ventana actualizada = ventanas.compute(clave, (k, vigente) -> {
            if (vigente == null || ahora - vigente.inicioEpochSegundos() >= duracionSegundos) {
                return new Ventana(ahora, 1);
            }
            return new Ventana(vigente.inicioEpochSegundos(), vigente.conteo() + 1);
        });

        boolean permitido = actualizada.conteo() <= capacidad;
        long segundosParaReintentar = duracionSegundos - (ahora - actualizada.inicioEpochSegundos());
        return new Resultado(permitido, Math.max(segundosParaReintentar, 1));
    }

    /** Olvida todas las ventanas. */
    public void reiniciar() {
        ventanas.clear();
    }

    /** Descarta las ventanas cuya ultima actividad ya quedo atras por dos ventanas completas. */
    public void limpiar() {
        long ahora = Instant.now(reloj).getEpochSecond();
        ventanas.entrySet().removeIf(
                entrada -> ahora - entrada.getValue().inicioEpochSegundos() >= duracionSegundos * 2L);
    }
}
