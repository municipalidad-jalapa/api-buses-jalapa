package gt.muni.jalapa.ecoruta.demanda.dominio;

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

import java.time.Instant;

/**
 * Reserva de un pasajero en una parada.
 *
 * <p>Reutiliza {@code registros_espera}: es el mismo concepto (alguien
 * esperando el bus) con el nombre de dominio "reserva". El nombre de tabla
 * es herencia del esquema inicial, no un segundo modelo.
 *
 * <p>OJO: el DEFAULT de la columna {@code estado} es {@code 'ACTIVO'} y no
 * hay CHECK. Los literales de {@link EstadoReserva} usan {@code ACTIVA}.
 * No es un bug de este mapeo: las historias de registro y cancelacion del
 * pasajero todavia no existen en codigo; ellas tendran que alinear default,
 * enum y escrituras.
 *
 * <p>{@code dispositivo_id} y {@code creado_en} no se mapean: este modulo
 * no los consulta.
 */
@Entity
@Table(name = "registros_espera")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    /** FK escalar a proposito: se filtra por parada, no se navega la entidad. */
    @Column(name = "parada_id", nullable = false)
    @ToString.Include
    private Long paradaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ToString.Include
    private EstadoReserva estado;

    @Column(name = "expira_en", nullable = false)
    @ToString.Include
    private Instant expiraEn;
}
