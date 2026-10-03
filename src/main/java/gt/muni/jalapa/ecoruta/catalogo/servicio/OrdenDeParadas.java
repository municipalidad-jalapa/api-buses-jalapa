package gt.muni.jalapa.ecoruta.catalogo.servicio;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * El orden de las paradas sale del recorrido, no de cuando se crearon: la
 * parada que cae antes sobre la linea es la de numero menor (informe de QA:
 * "al agregar una parada se pierde el orden del recorrido").
 *
 * <p>Donde cae cada parada se calcula en PostGIS con {@code ST_LineLocatePoint}
 * (la fraccion de la linea mas cercana). Una ruta de ida y vuelta que pasa dos
 * veces por la misma calle es ambigua: por eso una parada nueva o movida se
 * INSERTA entre las demas sin reordenarlas, y solo un borrador se renumera
 * completo cuando cambia su recorrido.
 *
 * <p>Sin trazado no se toca nada: el orden sigue siendo el de creacion. Los dos
 * metodos dejan la sesion vacia: quien llama vuelve a leer la ruta.
 */
@Component
@RequiredArgsConstructor
public class OrdenDeParadas {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    /** Renumera todas las paradas vigentes segun donde caen sobre el trazado. */
    public void segunElRecorrido(Long rutaId) {
        entityManager.flush();
        List<Long> ids = jdbc.queryForList("""
                SELECT p.id
                  FROM paradas p
                  JOIN rutas r ON r.id = p.ruta_id
                 WHERE p.ruta_id = ?
                   AND p.retirada_en IS NULL
                   AND r.trazado IS NOT NULL
                 ORDER BY ST_LineLocatePoint(r.trazado, p.ubicacion), p.orden
                """, Long.class, rutaId);
        aplicar(rutaId, ids);
        entityManager.clear();
    }

    /**
     * Pone una parada en su lugar del recorrido sin mover el orden relativo de
     * las demas: va antes de la primera (en su orden actual) que cae mas
     * adelante sobre la linea.
     */
    public void ubicar(Long rutaId, Long paradaId) {
        entityManager.flush();
        List<Fila> filas = jdbc.query("""
                        SELECT p.id, ST_LineLocatePoint(r.trazado, p.ubicacion) AS fraccion
                          FROM paradas p
                          JOIN rutas r ON r.id = p.ruta_id
                         WHERE p.ruta_id = ?
                           AND p.retirada_en IS NULL
                           AND r.trazado IS NOT NULL
                         ORDER BY p.orden
                        """,
                (rs, i) -> new Fila(rs.getLong("id"), rs.getDouble("fraccion")), rutaId);
        Fila esta = filas.stream().filter(f -> f.id().equals(paradaId)).findFirst().orElse(null);
        if (esta != null) {
            List<Long> ids = new ArrayList<>();
            boolean puesta = false;
            for (Fila otra : filas) {
                if (otra.id().equals(paradaId)) {
                    continue;
                }
                if (!puesta && otra.fraccion() > esta.fraccion()) {
                    ids.add(paradaId);
                    puesta = true;
                }
                ids.add(otra.id());
            }
            if (!puesta) {
                ids.add(paradaId);
            }
            aplicar(rutaId, ids);
        }
        entityManager.clear();
    }

    /**
     * Numera 1..n en el orden dado. En dos pasos: uq_parada_vigente_por_orden se
     * revisa fila por fila y pasar por negativos evita choques a mitad del cambio.
     */
    private void aplicar(Long rutaId, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Object[]> numeros = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            numeros.add(new Object[] {-(i + 1), ids.get(i)});
        }
        jdbc.batchUpdate("UPDATE paradas SET orden = ? WHERE id = ?", numeros);
        jdbc.update("UPDATE paradas SET orden = -orden WHERE ruta_id = ? AND retirada_en IS NULL AND orden < 0",
                rutaId);
    }

    private record Fila(Long id, double fraccion) {
    }
}
