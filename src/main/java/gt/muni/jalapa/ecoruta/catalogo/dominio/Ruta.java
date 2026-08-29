package gt.muni.jalapa.ecoruta.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.List;

/** Una ruta del bus, con sus paradas en orden. */
@Entity
@Table(name = "rutas")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    @ToString.Include
    private String nombre;

    /**
     * El recorrido dibujado sobre las calles. Nullable.
     *
     * <p>Hoy trae una ruta de EJEMPLO (V6), util para la demo pero inventada.
     * El recorrido de verdad lo carga SCRUM-136, que necesita levantamiento en
     * campo: que esta columna tenga dato no significa que sea el bueno.
     *
     * <p>El mapa no depende de esto: si viene vacio dibuja la linea uniendo las
     * paradas en su orden. El trazado solo hace que siga las calles.
     */
    @Column(name = "trazado")
    private LineString trazado;

    @Column(name = "activa", nullable = false)
    @ToString.Include
    private boolean activa = true;

    /**
     * Las paradas llegan ordenadas por el campo `orden`, no por id: es el orden
     * del recorrido y es lo que el mapa usa para trazar la linea.
     */
    @OneToMany(mappedBy = "ruta", fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<Parada> paradas = new ArrayList<>();
}
