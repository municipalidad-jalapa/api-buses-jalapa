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

/** Registro de espera del pasajero. Tabla historica {@code registros_espera}. */
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

    @Column(name = "dispositivo_id", nullable = false, length = 36)
    @ToString.Include
    private String dispositivoId;

    @Column(name = "parada_id", nullable = false)
    private Long paradaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    private Boolean subio;

    @Enumerated(EnumType.STRING)
    @Column(name = "abordaje_fuente", length = 20)
    private FuenteAbordaje abordajeFuente;

    @Column(name = "abordaje_en")
    private Instant abordajeEn;
}
