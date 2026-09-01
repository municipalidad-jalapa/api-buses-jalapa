package gt.muni.jalapa.ecoruta.demanda.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados por los que pasa una reserva de lugar en la parada (Desarrollo-135).
 *
 * <ul>
 *   <li>{@code ACTIVA}    recien creada, con vigencia por delante.
 *   <li>{@code RENOVADA}  se renovo antes de vencer; conserva su identificador.
 *   <li>{@code ABORDO}    el pasajero ya subio al bus.
 *   <li>{@code CANCELADA} el pasajero la solto a mano.
 *   <li>{@code EXPIRADA}  vencio la vigencia; la marca la tarea programada.
 * </ul>
 */
public enum EstadoReserva {
    ACTIVA,
    RENOVADA,
    ABORDO,
    CANCELADA,
    EXPIRADA;

    /** Estados desde los que una reserva todavia se puede renovar. */
    public static final Set<EstadoReserva> RENOVABLES = EnumSet.of(ACTIVA, RENOVADA);

    /**
     * Estados que ocupan el unico cupo vigente por dispositivo. Es el espejo en
     * codigo del indice unico parcial {@code uq_reserva_vigente_por_dispositivo}
     * (V7): si aqui y en la BD dejan de coincidir, el 422 amable y el fallo de
     * integridad dejan de decir lo mismo.
     */
    public static final Set<EstadoReserva> OCUPAN_CUPO = EnumSet.of(ACTIVA, RENOVADA, ABORDO);
}
