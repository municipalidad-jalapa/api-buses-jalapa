package gt.muni.jalapa.ecoruta.notificaciones.dominio;

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

@Entity
@Table(name = "fallos_de_aviso")
@Getter
@Setter
@NoArgsConstructor
public class FalloDeAviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id")
    private Long reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAviso tipo;

    @Column(columnDefinition = "text")
    private String detalle;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn = Instant.now();
}
