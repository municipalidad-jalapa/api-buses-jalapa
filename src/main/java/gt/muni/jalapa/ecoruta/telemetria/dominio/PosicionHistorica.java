package gt.muni.jalapa.ecoruta.telemetria.dominio;

import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/**
 * Una lectura de GPS ya persistida.
 *
 * <p>{@code vehiculo} es la atribucion duradera y se congela al insertar: por eso
 * cambiar el equipo del bus no pierde su historial (SCRUM-143). {@code equipo}
 * queda como dato forense de que aparato la envio, nunca como llave de consulta.
 */
@Entity
@Table(name = "posiciones_historicas")
@Getter
@Setter
@NoArgsConstructor
public class PosicionHistorica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PostGIS y JTS trabajan en (lon, lat). Construir siempre con {@code Geo}. */
    @Column(name = "ubicacion", nullable = false)
    private Point ubicacion;

    @Column(name = "velocidad_kmh")
    private Double velocidadKmh;

    /** Reloj del dispositivo, no el de llegada. */
    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "recibido_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant recibidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id")
    private Equipo equipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    public PosicionHistorica(Point ubicacion, Double velocidadKmh, Instant registradoEn,
                             Equipo equipo, Vehiculo vehiculo) {
        this.ubicacion = ubicacion;
        this.velocidadKmh = velocidadKmh;
        this.registradoEn = registradoEn;
        this.equipo = equipo;
        this.vehiculo = vehiculo;
    }
}
