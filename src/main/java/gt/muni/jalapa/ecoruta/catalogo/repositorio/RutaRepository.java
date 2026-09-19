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

    /**
     * Una ruta con sus paradas ya cargadas, en una sola lectura.
     *
     * <p>El join fetch evita N+1 al armar el resumen. El orden del recorrido lo
     * fija {@code @OrderBy("orden ASC")} en la entidad; con join fetch Hibernate
     * no garantiza el orden de la coleccion, asi que {@link
     * gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse} reordena al mapear.
     */
    @Query("SELECT r FROM Ruta r LEFT JOIN FETCH r.paradas WHERE r.id = :id")
    Optional<Ruta> buscarConParadas(Long id);
}
