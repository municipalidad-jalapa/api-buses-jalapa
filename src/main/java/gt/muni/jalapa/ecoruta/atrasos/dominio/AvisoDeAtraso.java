package gt.muni.jalapa.ecoruta.atrasos.dominio;

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

import java.time.Instant;

/**
 * Aviso de atraso reportado por el piloto (SCRUM-26, bloque E).
 *
 * <p>Vive mientras dura la demora estimada; despues queda como historial de lo
 * que paso en la ruta. Nunca se borra: cancelarlo es marcar {@code canceladoEn}.
 */
@Entity
@Table(name = "avisos_de_atraso")
@Getter
@Setter
@NoArgsConstructor
public class AvisoDeAtraso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruta_id", nullable = false)
    private Long rutaId;

    /** El bus que llevaba la ruta al reportar; null si la ruta no tenia ninguno. */
    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @Column(name = "conductor", nullable = false, length = 50)
    private String conductor;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 20)
    private MotivoDeAtraso motivo;

    @Column(name = "demora_minutos", nullable = false)
    private int demoraMinutos;

    @Column(name = "comentario", length = 200)
    private String comentario;

    @Column(name = "reportado_en", nullable = false)
    private Instant reportadoEn;

    @Column(name = "vigente_hasta", nullable = false)
    private Instant vigenteHasta;

    @Column(name = "cancelado_en")
    private Instant canceladoEn;

    public boolean estaVigente(Instant ahora) {
        return canceladoEn == null && ahora.isBefore(vigenteHasta);
    }

    public void cancelar(Instant ahora) {
        this.canceladoEn = ahora;
    }
}
