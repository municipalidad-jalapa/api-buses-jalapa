package gt.muni.jalapa.ecoruta.identidad.dominio;

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
import lombok.ToString;

/**
 * Cuenta de persona. Reutiliza la tabla {@code usuarios} de V1; el uid de
 * Firebase se agrego en V6 para saber si el idToken corresponde a un conductor.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @ToString.Include
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "firebase_uid", unique = true, length = 128)
    private String firebaseUid;

    public boolean puedeIniciarSesionComoConductor() {
        return activo && rol.esConductor();
    }
}
