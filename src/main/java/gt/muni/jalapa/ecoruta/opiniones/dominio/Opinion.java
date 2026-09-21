package gt.muni.jalapa.ecoruta.opiniones.dominio;

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
 * Opinion de un pasajero sobre el servicio (SCRUM-26, bloque A).
 *
 * <p>Ruta, vehiculo y reserva se guardan como ids: el modulo no navega el
 * catalogo ni la flota, y el vehiculo es el que prestaba el servicio al
 * registrar, aunque despues se reasigne.
 */
@Entity
@Table(name = "opiniones")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Opinion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private TipoOpinion tipo;

    @Column(name = "ruta_id", nullable = false)
    private Long rutaId;

    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @Column(name = "reserva_id")
    private Long reservaId;

    @Column(name = "dispositivo_id", nullable = false, length = 64)
    private String dispositivoId;

    /** Cuenta del pasajero si opino con sesion (bloque B); null si fue como invitado. */
    @Column(name = "pasajero_id")
    private Long pasajeroId;

    /** Tal como lo escribio la persona. Se neutraliza al devolverlo. */
    @Column(name = "texto")
    private String texto;

    @Column(name = "estrellas")
    private Integer estrellas;

    /**
     * SCRUM-26, bloque F: tres valoraciones independientes, de 1 a 5, que la
     * gente puntua por separado porque miden cosas distintas. Opcionales: una
     * opinion puede traer solo texto, solo la calificacion general o cualquier
     * combinacion.
     */
    @Column(name = "calidad")
    private Integer calidad;

    @Column(name = "limpieza")
    private Integer limpieza;

    @Column(name = "conduccion")
    private Integer conduccion;

    @Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadaEn;

    @Column(name = "atendida_en")
    private Instant atendidaEn;

    @Column(name = "atendida_por", length = 100)
    private String atendidaPor;

    public boolean estaAtendida() {
        return atendidaEn != null;
    }
}
