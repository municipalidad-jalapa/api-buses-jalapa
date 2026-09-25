package gt.muni.jalapa.ecoruta.precision.dominio;

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

import java.time.Instant;

/**
 * Una prediccion de llegada persistida (HU-73).
 *
 * <p>No calcula el ETA: solo guarda lo que otro modulo (HU-71) o el generador
 * de prueba le entrega, con ruta, parada, vehiculo y marca de tiempo.
 */
@Entity
@Table(name = "predicciones_eta")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PrediccionEta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "ruta_id", nullable = false)
    @ToString.Include
    private Long rutaId;

    @Column(name = "parada_id", nullable = false)
    @ToString.Include
    private Long paradaId;

    @Column(name = "vehiculo_id", nullable = false)
    @ToString.Include
    private Long vehiculoId;

    @Column(name = "eta_predicho_min", nullable = false)
    private int etaPredichoMin;

    @Column(name = "predicho_en", nullable = false)
    private Instant predichoEn;

    /** Dato de prueba. false cuando HU-71 publique predicciones reales. */
    @Column(name = "simulada", nullable = false)
    private boolean simulada;

    public PrediccionEta(Long rutaId, Long paradaId, Long vehiculoId,
                         int etaPredichoMin, Instant predichoEn, boolean simulada) {
        this.rutaId = rutaId;
        this.paradaId = paradaId;
        this.vehiculoId = vehiculoId;
        this.etaPredichoMin = etaPredichoMin;
        this.predichoEn = predichoEn;
        this.simulada = simulada;
    }
}
