package gt.muni.jalapa.ecoruta.precision.repositorio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Lectura de paradas para detectar llegada. No modifica el catalogo (HU-73). */
public interface ParadasParaPrecisionRepository extends JpaRepository<Parada, Long> {

    @Query("SELECT p FROM Parada p JOIN FETCH p.ruta")
    List<Parada> todasConRuta();
}
