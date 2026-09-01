package gt.muni.jalapa.ecoruta.demanda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * La reserva de un lugar en la parada, hecha por un dispositivo del pasajero
 * (Desarrollo-135). Mapea la tabla {@code registros_espera} de V1.
 *
 * <p>Nace {@link EstadoReserva#ACTIVA} con una vigencia de pocos minutos que
 * viaja en {@link #expiraEn}. Renovarla la deja {@link EstadoReserva#RENOVADA}
 * SIN cambiar su identificador; si la vigencia vence, la tarea programada la
 * pasa a {@link EstadoReserva#EXPIRADA}.
 */
@Entity
@Table(name = "registros_espera")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    /** Identificador opaco del dispositivo del pasajero. No hay login. */
    @Column(name = "dispositivo_id", nullable = false, length = 36, updatable = false)
    @ToString.Include
    private String dispositivoId;

    /** Parada en la que espera. Se guarda el id pelado: aqui no se navega la parada. */
    @Column(name = "parada_id", nullable = false, updatable = false)
    @ToString.Include
    private Long paradaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ToString.Include
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadoEn;

    /** Momento en que la reserva deja de estar vigente. Lo fija el servicio. */
    @Column(name = "expira_en", nullable = false)
    @ToString.Include
    private Instant expiraEn;

    public Reserva(String dispositivoId, Long paradaId, Instant expiraEn) {
        this.dispositivoId = dispositivoId;
        this.paradaId = paradaId;
        this.expiraEn = expiraEn;
    }

    /** Vigente = renovable por estado y con la fecha de expiracion aun en el futuro. */
    public boolean estaVigente(Instant ahora) {
        return EstadoReserva.RENOVABLES.contains(estado) && expiraEn.isAfter(ahora);
    }

    /** Extiende la vigencia y deja constancia de que se renovo. Mismo identificador. */
    public void renovar(Instant nuevoExpiraEn) {
        this.expiraEn = nuevoExpiraEn;
        this.estado = EstadoReserva.RENOVADA;
    }

    public void expirar() {
        this.estado = EstadoReserva.EXPIRADA;
    }
}
