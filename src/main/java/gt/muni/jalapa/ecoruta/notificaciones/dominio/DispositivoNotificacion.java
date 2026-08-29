package gt.muni.jalapa.ecoruta.notificaciones.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dispositivos_notificacion")
@Getter
@Setter
@NoArgsConstructor
public class DispositivoNotificacion {

    @Id
    @Column(name = "dispositivo_id", length = 36)
    private String dispositivoId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();
}
