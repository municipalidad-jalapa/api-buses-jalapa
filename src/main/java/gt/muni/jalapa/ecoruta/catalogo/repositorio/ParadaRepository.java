package gt.muni.jalapa.ecoruta.catalogo.repositorio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a paradas del catalogo.
 *
 * <p>La geocerca se resuelve en PostGIS con {@code geography} (ADR-007): metros
 * reales sobre el elipsoide, no grados de {@code geometry} SRID 4326.
 */
public interface ParadaRepository extends JpaRepository<Parada, Long> {

    /**
     * ¿El punto (latitud, longitud) cae dentro de la geocerca de la parada?
     *
     * <p>PostGIS recibe primero longitud y despues latitud en
     * {@code ST_MakePoint}.
     */
    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                  FROM paradas p
                 WHERE p.id = :paradaId
                   AND ST_DWithin(
                         p.ubicacion::geography,
                         ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography,
                         :geocercaMetros
                       )
            )
            """, nativeQuery = true)
    boolean estaDentroDeGeocerca(@Param("paradaId") Long paradaId,
                                 @Param("latitud") double latitud,
                                 @Param("longitud") double longitud,
                                 @Param("geocercaMetros") double geocercaMetros);
}
