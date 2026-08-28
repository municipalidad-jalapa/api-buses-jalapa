package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.Parada;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Existencia de paradas. Sin metodos propios: {@code existsById} alcanza
 * para rechazar consultas contra una parada que no existe, y este modulo
 * no lista ni escribe paradas.
 */
public interface ParadaRepository extends JpaRepository<Parada, Long> {
}
