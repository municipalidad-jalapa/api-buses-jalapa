package gt.muni.jalapa.ecoruta.seguridad.conductor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * PROVISIONAL — TODO(SCRUM-134).
 *
 * <p>Mapeo de lectura de {@code usuarios} para que un conductor pueda obtener
 * {@code ROLE_CONDUCTOR} HOY. Existe solo porque {@code GET
 * /api/v1/paradas/{id}/reservas} exige ese rol y todavia no hay login de
 * personas (Firebase, SCRUM-134 / ADR-010). Sin este paquete nadie puede
 * autenticarse como conductor y el endpoint queda inservible.
 *
 * <p>Vive en {@code seguridad.conductor}, no en {@code identidad/}, a
 * proposito: {@code identidad/} esta reservado para el login real con
 * Firebase. Meter esto ahi seria mezclar un atajo temporal con el modelo
 * definitivo y obligar a reescribir el paquete el dia que aterrice SCRUM-134.
 *
 * <p>Es de solo lectura: no hay alta ni cambio de contrasena en este
 * habilitador. Por eso no hay setters. {@code passwordHash} queda fuera de
 * {@code toString} para no terminar en un log.
 *
 * <p><b>Para borrarlo cuando aterrice SCRUM-134:</b> borrar este paquete
 * entero; quitar el bloque marcado en {@code SecurityConfig}; borrar sus
 * pruebas. Los controladores de demanda NO cambian: siguen exigiendo
 * {@code hasRole('CONDUCTOR')}; solo cambia quien concede el rol.
 */
@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "username", nullable = false, length = 50, updatable = false)
    @ToString.Include
    private String username;

    /**
     * bcrypt. Sensible: fuera de toString, fuera de logs, fuera de todo DTO.
     */
    @Column(name = "password_hash", nullable = false, length = 255, updatable = false)
    private String passwordHash;

    /** VARCHAR heredado del esquema; no es un enum para no fingir modelo de identidad. */
    @Column(name = "rol", nullable = false, length = 20, updatable = false)
    @ToString.Include
    private String rol;

    @Column(name = "activo", nullable = false, updatable = false)
    @ToString.Include
    private boolean activo;
}
