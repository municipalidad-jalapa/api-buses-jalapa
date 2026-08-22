package gt.muni.jalapa.ecoruta.flota.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Un bus de la flota. El historico de recorrido cuelga de aqui, no del equipo. */
@Entity
@Table(name = "vehiculos")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    /** Numero economico, ej. "BUS-01". No cambia una vez asignado. */
    @Column(name = "identificador", nullable = false, length = 30, updatable = false)
    @ToString.Include
    private String identificador;

    @Column(name = "placa", nullable = false, length = 15)
    @ToString.Include
    private String placa;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadoEn;

    public Vehiculo(String identificador, String placa) {
        this.identificador = identificador;
        this.placa = placa;
    }
}
