package gt.muni.jalapa.ecoruta.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/**
 * Una parada del recorrido.
 *
 * <p>En Jalapa las paradas no estan senalizadas fisicamente, asi que el nombre
 * es una referencia reconocible --una esquina, un negocio, un hito-- y no un
 * nombre oficial.
 */
@Entity
@Table(name = "paradas")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Parada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    @ToString.Include
    private String nombre;

    /** PostGIS y JTS trabajan en (lon, lat). Leer siempre con {@code Geo}. */
    @Column(name = "ubicacion", nullable = false)
    private Point ubicacion;

    /** Posicion dentro del recorrido, empezando en 1. Unico por ruta. */
    @Column(name = "orden", nullable = false)
    @ToString.Include
    private int orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id", nullable = false)
    private Ruta ruta;

    /**
     * Cuando se elimino del recorrido desde el panel (V30). La fila se queda
     * porque el historial la referencia; null = vigente.
     */
    @Column(name = "retirada_en")
    private Instant retiradaEn;

    public boolean estaRetirada() {
        return retiradaEn != null;
    }
}
