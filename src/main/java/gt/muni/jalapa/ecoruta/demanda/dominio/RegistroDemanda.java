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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "registros_espera")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class RegistroDemanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "dispositivo_id", nullable = false, length = 36)
    private String dispositivoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parada_id", nullable = false)
    private Parada parada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRegistroDemanda estado = EstadoRegistroDemanda.ACTIVO;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "cancelado_en")
    private Instant canceladoEn;

    public RegistroDemanda(
            String dispositivoId,
            Parada parada,
            Instant expiraEn) {
        this.dispositivoId = dispositivoId;
        this.parada = parada;
        this.expiraEn = expiraEn;
        this.estado = EstadoRegistroDemanda.ACTIVO;
    }
}