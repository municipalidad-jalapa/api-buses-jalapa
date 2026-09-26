package gt.muni.jalapa.ecoruta.precision.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Llegada detectada por telemetria, asociada a la prediccion vigente (HU-73). */
@Entity
@Table(name = "llegadas_reales")
@Getter
@Setter
@NoArgsConstructor
public class LlegadaReal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediccion_id", nullable = false, unique = true)
    private PrediccionEta prediccion;

    @Column(name = "llegada_en", nullable = false)
    private Instant llegadaEn;

    @Column(name = "error_min", nullable = false)
    private double errorMin;

    public LlegadaReal(PrediccionEta prediccion, Instant llegadaEn, double errorMin) {
        this.prediccion = prediccion;
        this.llegadaEn = llegadaEn;
        this.errorMin = errorMin;
    }
}
