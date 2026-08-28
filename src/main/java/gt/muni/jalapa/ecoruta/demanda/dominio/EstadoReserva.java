package gt.muni.jalapa.ecoruta.demanda.dominio;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de una reserva de espera en parada.
 *
 * <p>Los literales van en femenino porque el concepto de dominio es
 * {@code Reserva}, no el nombre heredado de la tabla ({@code registros_espera}).
 */
public enum EstadoReserva {
    ACTIVA,
    RENOVADA,
    ABORDO,
    CANCELADA,
    EXPIRADA;

    private static final Set<EstadoReserva> VIGENTES =
            Collections.unmodifiableSet(EnumSet.of(ACTIVA, RENOVADA, ABORDO));

    /**
     * Los tres estados que todavia cuentan como demanda en la parada.
     *
     * <p>{@code ACTIVA} es la espera recien creada. {@code RENOVADA} es la
     * misma espera con el TTL extendido: el pasajero sigue ahi, no es una
     * reserva nueva. {@code ABORDO} entra porque el pasajero ya fue tomado
     * por un bus pero la fila no se libero; sigue ocupando demanda hasta
     * que el abordaje cierre.
     *
     * <p>{@code CANCELADA} y {@code EXPIRADA} quedan fuera a proposito: son
     * terminales. El pasajero ya no espera (se fue o se le vencio el tiempo)
     * y contarlos inflaria la demanda de la parada.
     */
    public static Set<EstadoReserva> vigentes() {
        return VIGENTES;
    }
}
