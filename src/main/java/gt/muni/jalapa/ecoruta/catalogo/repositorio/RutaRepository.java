package gt.muni.jalapa.ecoruta.catalogo.repositorio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RutaRepository extends JpaRepository<Ruta, Long> {

    /**
     * Las rutas activas con sus paradas ya cargadas.
     *
     * <p>El join fetch no es opcional: open-in-view esta en false, asi que fuera
     * de la transaccion la coleccion LAZY ya no se puede recorrer. DISTINCT
     * porque el join multiplica la fila de la ruta por cada parada.
     */
    @Query("""
            SELECT DISTINCT r FROM Ruta r
            LEFT JOIN FETCH r.paradas
            WHERE r.activa = true
            ORDER BY r.id
            """)
    List<Ruta> buscarActivasConParadas();

    @Query("SELECT r FROM Ruta r LEFT JOIN FETCH r.paradas WHERE r.id = :id")
    Optional<Ruta> buscarConParadas(Long id);
}
