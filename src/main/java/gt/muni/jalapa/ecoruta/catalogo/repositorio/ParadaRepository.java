package gt.muni.jalapa.ecoruta.catalogo.repositorio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso directo a paradas. Hoy solo lo usa el modulo demanda para comprobar
 * que la parada de una reserva existe antes de crearla (Desarrollo-135); las
 * consultas del catalogo van por {@code RutaRepository} con las paradas ya
 * cargadas.
 */
public interface ParadaRepository extends JpaRepository<Parada, Long> {
}
