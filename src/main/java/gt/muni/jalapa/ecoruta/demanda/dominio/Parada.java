package gt.muni.jalapa.ecoruta.demanda.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Parada vista desde demanda: solo hace falta saber si existe.
 *
 * <p>No se mapean {@code nombre}, {@code ubicacion} (geometry) ni
 * {@code ruta_id} a proposito. Este modulo no lista paradas ni calcula
 * distancias; arrastrar PostGIS/JTS aqui acoplaria demanda a telemetria
 * sin ganancia. {@code existsById} alcanza.
 */
@Entity
@Table(name = "paradas")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Parada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;
}
