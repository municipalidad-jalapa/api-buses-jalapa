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
 * Registro de un pasajero esperando en una parada ({@code registros_espera}).
 *
 * <p>Nace {@link EstadoReserva#ACTIVA} con una vigencia de pocos minutos que
 * viaja en {@link #expiraEn}. Renovarla la deja {@link EstadoReserva#RENOVADA}
 * SIN cambiar su identificador; si la vigencia vence, la tarea programada la
 * pasa a {@link EstadoReserva#EXPIRADA}.
 *
 * <p>SCRUM-306 crea la reserva en estado {@link EstadoReserva#ACTIVA}. HU-135
 * la renueva, HU-124 la cancela y HU-57 registra si el pasajero logro subir.
 *
 * <p>HU-76 permite que el conductor marque una parada como atendida y conserva
 * informacion adicional de auditoria.
 *
 * <p>La parada se mapea como relacion y no como un {@code Long} suelto.
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
    @Column(name = "dispositivo_id", nullable = false, length = 36)
    @ToString.Include
    private String dispositivoId;

    /**
     * SCRUM-26, bloque B. Cuenta del pasajero, si la reserva quedo vinculada a
     * una. Opcional a proposito: el uso anonimo sigue funcionando sin cuenta.
     */
    @Column(name = "pasajero_id")
    private Long pasajeroId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parada_id", nullable = false)
    private Parada parada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ToString.Include
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /** Momento en que la reserva deja de estar vigente. Lo fija el servicio. */
    @Column(name = "expira_en", nullable = false)
    @ToString.Include
    private Instant expiraEn;

    /**
     * HU-57.
     * Respuesta de abordaje.
     * null mientras nadie responde.
     */
    @Column(name = "subio")
    private Boolean subio;

    /**
     * HU-57.
     * Quien registro la respuesta de abordaje:
     * pasajero o conductor.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "abordaje_fuente", length = 20)
    private FuenteAbordaje abordajeFuente;

    /**
     * HU-57.
     * Momento de la ultima respuesta de abordaje.
     */
    @Column(name = "abordaje_en")
    private Instant abordajeEn;

    /**
     * HU-76.
     * Usuario del conductor que confirmo
     * que el pasajero abordo.
     */
    @Column(name = "abordado_por", length = 50)
    private String abordadoPor;

    /**
     * HU-76.
     * Conserva la declaracion del pasajero cuando
     * indica que no logro abordar.
     *
     * Esta informacion no se elimina aunque luego
     * el conductor confirme que si abordo.
     */
    @Column(
            name = "pasajero_declaro_no_abordo",
            nullable = false
    )
    private boolean pasajeroDeclaroNoAbordo = false;

    /**
     * HU-76.
     * Momento en que el pasajero indico
     * que no abordo.
     */
    @Column(name = "declaracion_no_abordo_en")
    private Instant declaracionNoAbordoEn;

    /**
     * HU-124.
     * Momento en que el pasajero cancelo manualmente
     * su reserva.
     */
    @Column(name = "cancelado_en")
    private Instant canceladoEn;

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

    public Reserva(String dispositivoId, Parada parada, Instant expiraEn) {
        this(dispositivoId, parada, EstadoReserva.ACTIVA, Instant.now(), expiraEn);
    }

    /**
     * Atajo para quien solo necesita
     * el identificador de la parada.
     */
    public Long getParadaId() {
        return parada == null ? null : parada.getId();
    }

    /**
     * HU-135.
     * Vigente = estado renovable y fecha
     * de expiracion aun en el futuro.
     */
    public boolean estaVigente(Instant ahora) {
        return EstadoReserva.RENOVABLES.contains(estado)
                && expiraEn.isAfter(ahora);
    }

    /**
     * HU-135.
     * Extiende la vigencia y conserva
     * el mismo identificador.
     */
    public void renovar(Instant nuevoExpiraEn) {
        this.expiraEn = nuevoExpiraEn;
        this.estado = EstadoReserva.RENOVADA;
    }

    /**
     * HU-135.
     * Marca la reserva como expirada.
     */
    public void expirar() {
        this.estado = EstadoReserva.EXPIRADA;
    }

    /**
     * HU-124.
     * Cancela manualmente la reserva
     * y conserva la fecha de cancelacion.
     */
    public void cancelar(Instant ahora) {
        this.estado = EstadoReserva.CANCELADA;
        this.canceladoEn = ahora;
    }

    /**
     * Comprueba si la reserva pertenece
     * al dispositivo indicado.
     */
    public boolean perteneceA(String dispositivoId) {
        return this.dispositivoId.equals(dispositivoId);
    }

    /**
     * SCRUM-26, bloque B.2. La reserva es de quien la creo desde este
     * navegador o de la cuenta a la que quedo vinculada. Asi el pasajero con
     * sesion sigue viendo y cancelando lo suyo desde otro telefono.
     *
     * @param pasajeroId cuenta autenticada; null si entra como invitado
     */
    public boolean perteneceA(String dispositivoId, Long pasajeroId) {
        return perteneceA(dispositivoId)
                || (pasajeroId != null && pasajeroId.equals(this.pasajeroId));
    }

    /**
     * HU-76.
     *
     * El pasajero declara que no logro abordar.
     *
     * La declaracion se conserva por separado
     * para que no se pierda si posteriormente
     * el conductor confirma el abordaje.
     */
    public void declararNoAbordo(Instant momento) {
        this.pasajeroDeclaroNoAbordo = true;
        this.declaracionNoAbordoEn = momento;

        this.subio = false;
        this.abordajeFuente = FuenteAbordaje.PASAJERO;
        this.abordajeEn = momento;
    }

    /**
     * HU-76.
     *
     * El conductor confirma que el pasajero abordo.
     * La confirmacion del conductor tiene prioridad.
     *
     * Si anteriormente el pasajero habia indicado
     * que no abordo, esa declaracion se conserva
     * para auditoria.
     */
    public void marcarAbordo(
            String conductor,
            Instant momento
    ) {
        this.estado = EstadoReserva.ABORDO;

        this.subio = true;
        this.abordajeFuente = FuenteAbordaje.CONDUCTOR;
        this.abordajeEn = momento;

        this.abordadoPor = conductor;
    }
}