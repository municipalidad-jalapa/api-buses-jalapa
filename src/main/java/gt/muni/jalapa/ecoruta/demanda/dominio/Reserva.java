package gt.muni.jalapa.ecoruta.demanda.dominio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Registro de un pasajero esperando en una parada.
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

    @Column(name = "dispositivo_id", nullable = false, length = 36)
    @ToString.Include
    private String dispositivoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parada_id", nullable = false)
    private Parada parada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ToString.Include
    private EstadoReserva estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    @ToString.Include
    private Instant expiraEn;

    /**
     * Momento en que el conductor marco
     * que el pasajero ya abordo.
     */
    @Column(name = "abordado_en")
    private Instant abordadoEn;

    /**
     * Usuario del conductor que realizo
     * la accion.
     */
    @Column(name = "abordado_por", length = 50)
    private String abordadoPor;

    /**
     * Declaracion realizada por el pasajero.
     *
     * Se guarda separada del estado de la reserva
     * porque la confirmacion del conductor tiene
     * prioridad.
     */
    @Column(
            name = "pasajero_declaro_no_abordo",
            nullable = false
    )
    private boolean pasajeroDeclaroNoAbordo;

    /**
     * Momento en que el pasajero indico
     * que no abordo el bus.
     */
    @Column(name = "declaracion_no_abordo_en")
    private Instant declaracionNoAbordoEn;

    public Reserva(
            String dispositivoId,
            Parada parada,
            EstadoReserva estado,
            Instant creadoEn,
            Instant expiraEn
    ) {
        this.dispositivoId = dispositivoId;
        this.parada = parada;
        this.estado = estado;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
        this.pasajeroDeclaroNoAbordo = false;
    }

    /**
     * HU-76.
     *
     * La declaracion del pasajero se conserva
     * independientemente del estado final.
     */
    public void declararNoAbordo(Instant momento) {
        this.pasajeroDeclaroNoAbordo = true;
        this.declaracionNoAbordoEn = momento;
    }

    /**
     * HU-76.
     *
     * La confirmacion del conductor tiene prioridad.
     * La reserva pasa a ABORDO, pero NO se elimina
     * la declaracion previa del pasajero.
     */
    public void marcarAbordo(
            String conductor,
            Instant momento
    ) {
        this.estado = EstadoReserva.ABORDO;
        this.abordadoPor = conductor;
        this.abordadoEn = momento;
    }
}