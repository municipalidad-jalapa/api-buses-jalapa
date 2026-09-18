package gt.muni.jalapa.ecoruta.demanda.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados oficiales de una reserva en la parada (SCRUM-306).
 *
 * <ul>
 *   <li>{@code ACTIVA}    recien creada, con vigencia por delante.
 *   <li>{@code RENOVADA}  se renovo antes de vencer; conserva su identificador (HU-135).
 *   <li>{@code ABORDO}    el pasajero ya subio al bus (HU-57).
 *   <li>{@code CANCELADA} el pasajero la solto a mano (HU-124).
 *   <li>{@code EXPIRADA}  vencio la vigencia; la marca la tarea programada (HU-135).
 * </ul>
 *
 * <p>Solo {@link #ACTIVA} y {@link #RENOVADA} cuentan como vigentes. El valor
 * legado {@code ACTIVO} de V1 no forma parte de este enumerado: las reservas
 * nuevas se crean siempre como {@link #ACTIVA}.
 */
public enum EstadoReserva {
    ACTIVA,
    RENOVADA,
    ABORDO,
    CANCELADA,
    EXPIRADA;

    /**
     * Estados vigentes: se pueden renovar, cancelar y expirar, y son los unicos
     * que ocupan el cupo de un dispositivo.
     *
     * <p>HU-135 incluia ademas {@code ABORDO} en el cupo. Se descarto al integrar:
     * la tarea programada solo expira estos dos estados, asi que una reserva en
     * {@code ABORDO} no vence nunca y el pasajero que subio una vez no podria
     * volver a reservar. Coincide con el indice unico parcial de V7.
     */
    public static final Set<EstadoReserva> RENOVABLES = EnumSet.of(ACTIVA, RENOVADA);

    /** Vigente para recibir avisos de aproximacion (HU-57). */
    public boolean vigenteParaAviso() {
        return RENOVABLES.contains(this);
    }

    public boolean permiteAbordajePasajero() {
        return vigenteParaAviso();
    }

    /** El conductor puede corregir incluso despues de la respuesta del pasajero. */
    public boolean permiteAbordajeConductor() {
        return this == ACTIVA || this == RENOVADA || this == ABORDO || this == CANCELADA;
    }
}
