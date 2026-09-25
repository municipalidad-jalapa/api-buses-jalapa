package gt.muni.jalapa.ecoruta.integraciones.traccar.dominio;

import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
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

@Entity
@Table(name = "dispositivos_externos")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class DispositivoExterno {

    public static final String TRACCAR = "TRACCAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "proveedor", nullable = false, length = 20)
    @ToString.Include
    private String proveedor = TRACCAR;

    @Column(name = "identificador", nullable = false, length = 64)
    @ToString.Include
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_id")
    private Equipo equipo;
}
