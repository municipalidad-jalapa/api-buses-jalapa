package gt.muni.jalapa.ecoruta.pasajeros.dominio;

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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/** Cuenta opcional del pasajero (SCRUM-26, bloque B). La identidad vive en Firebase. */
@Entity
@Table(name = "pasajeros")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Pasajero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128, updatable = false)
    private String firebaseUid;

    @Column(name = "correo", length = 254)
    private String correo;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creadoEn;
}
