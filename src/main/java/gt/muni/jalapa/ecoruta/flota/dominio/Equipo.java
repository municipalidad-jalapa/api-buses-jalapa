package gt.muni.jalapa.ecoruta.flota.dominio;

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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * El equipo a bordo: el aparato montado en la cabina que reporta la posicion.
 *
 * <p>Su credencial es independiente de las cuentas de personas de {@code
 * identidad/} (SCRUM-142): no hay usuario, ni contrasena recuperable, ni rol de
 * persona. Por eso la ingesta no depende de que un conductor tenga sesion
 * abierta, y revocar este equipo no afecta a ningun otro.
 *
 * <p>OJO con {@code @ToString}: se usa {@code onlyExplicitlyIncluded} y jamas
 * {@code @Data} ni {@code @ToString} pelado, porque {@code secretoHash} no puede
 * terminar en un log.
 */
@Entity
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    /** Parte publica del token: identifica la fila y es lo unico que se puede loguear. */
    @Column(name = "codigo_publico", nullable = false, length = 32, updatable = false)
    @ToString.Include
    private String codigoPublico;

    /**
     * bcrypt del secreto. Sensible: fuera de toString, fuera de logs, fuera de
     * todo DTO.
     *
     * <p>{@code updatable = false} es deliberado: garantiza a nivel de mapeo que
     * la credencial no se puede rotar en el sitio. Cambiar de credencial es
     * revocar esta fila y crear otra.
     */
    @Column(name = "secreto_hash", nullable = false, length = 72, updatable = false)
    private String secretoHash;

    @Column(name = "etiqueta", nullable = false, length = 60)
    @ToString.Include
    private String etiqueta;

    /** El bus en el que va montado. NULL = en bodega, sin asignar (SCRUM-143). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ToString.Include
    private EstadoEquipo estado = EstadoEquipo.ACTIVO;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "ultimo_uso_en")
    private Instant ultimoUsoEn;

    public Equipo(String codigoPublico, String secretoHash, String etiqueta, Vehiculo vehiculo) {
        this.codigoPublico = codigoPublico;
        this.secretoHash = secretoHash;
        this.etiqueta = etiqueta;
        this.vehiculo = vehiculo;
    }

    public boolean estaActivo() {
        return estado == EstadoEquipo.ACTIVO;
    }

    /** Idempotente: revocar dos veces no mueve la fecha de la primera revocacion. */
    public void revocar(Instant cuando) {
        if (estado == EstadoEquipo.REVOCADO) {
            return;
        }
        this.estado = EstadoEquipo.REVOCADO;
        this.revocadoEn = cuando;
    }
}
