package gt.muni.jalapa.ecoruta.calles;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Camino mas corto por la red de calles de Jalapa (SCRUM-26, bloque C).
 *
 * <p>Las calles vienen de OpenStreetMap y viven en la base (migraciones V19 y
 * V20): en ejecucion no se consulta ningun servicio externo. El enrutado lo
 * hace pgRouting con {@code pgr_dijkstra} sobre aristas cuyo costo es la
 * longitud en metros y cuyo costo inverso vale -1 en las vias de un solo
 * sentido, de modo que el camino respeta el sentido segun OSM.
 *
 * <p>Si no hay calle cerca o no existe camino, no se inventa nada: se devuelve
 * vacio y quien llama vuelve a la estimacion por factor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedDeCalles {

    private final JdbcTemplate jdbc;
    private final RedDeCallesProperties propiedades;

    /**
     * Camino por calles desde el bus hasta el mejor de los destinos.
     *
     * <p>"Mejor" es el de menor distancia real por calles, no el mas cercano en
     * linea recta: es el criterio 4 del bloque C. Los destinos se prueban todos
     * en una sola llamada a pgr_dijkstra (uno a muchos).
     *
     * @param destinos candidatos de reincorporacion, en orden de trazado
     * @return vacio si la red no esta habilitada, no hay calle cerca o no hay
     *         camino posible
     */
    @Transactional(readOnly = true)
    public Optional<Camino> caminoMasCorto(PuntoResponse origen, List<PuntoResponse> destinos) {
        if (!propiedades.habilitada() || destinos.isEmpty() || !hayRed()) {
            return Optional.empty();
        }
        try {
            Long nodoOrigen = nodoCercano(origen);
            if (nodoOrigen == null) {
                log.debug("Desvio sin calle cerca del bus ({}, {}): se estima por factor",
                        origen.latitud(), origen.longitud());
                return Optional.empty();
            }

            List<Long> nodosDestino = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < destinos.size(); i++) {
                Long nodo = nodoCercano(destinos.get(i));
                if (nodo != null && !nodo.equals(nodoOrigen) && !nodosDestino.contains(nodo)) {
                    nodosDestino.add(nodo);
                    indices.add(i);
                }
            }
            if (nodosDestino.isEmpty()) {
                return Optional.empty();
            }

            Optional<Llegada> mejor = mejorLlegada(nodoOrigen, nodosDestino);
            if (mejor.isEmpty()) {
                log.debug("Sin camino por calles desde el nodo {}: se estima por factor", nodoOrigen);
                return Optional.empty();
            }

            int cual = nodosDestino.indexOf(mejor.get().nodo());
            int indiceDestino = indices.get(cual);
            List<PuntoResponse> trazo = trazoDelCamino(nodoOrigen, mejor.get().nodo());
            return Optional.of(new Camino(indiceDestino, mejor.get().metros(), trazo));

        } catch (DataAccessException ex) {
            // Que el enrutado falle no puede dejar al pasajero sin ETA.
            log.warn("Fallo el enrutado por calles, se estima por factor: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Un entorno sin la red importada sigue funcionando con el factor. */
    private boolean hayRed() {
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM calles LIMIT 1) c",
                Integer.class);
        return filas != null && filas > 0;
    }

    private Long nodoCercano(PuntoResponse punto) {
        return jdbc.query("""
                        SELECT n.id
                          FROM calles_nodos n
                         WHERE ST_DWithin(n.punto::geography,
                                          ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                         ORDER BY n.punto <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
                         LIMIT 1
                        """,
                (rs, i) -> rs.getLong("id"),
                punto.longitud(), punto.latitud(), propiedades.radioNodoMetros(),
                punto.longitud(), punto.latitud()).stream().findFirst().orElse(null);
    }

    /** Uno a muchos: de todos los candidatos alcanzables, el de menor costo. */
    private Optional<Llegada> mejorLlegada(Long origen, List<Long> destinos) {
        return jdbc.query("""
                        SELECT end_vid, agg_cost
                          FROM pgr_dijkstra(
                                 'SELECT id, origen AS source, destino AS target,
                                         costo AS cost, costo_reverso AS reverse_cost FROM calles',
                                 ?::bigint, ?::bigint[], directed => true)
                         WHERE edge = -1
                         ORDER BY agg_cost
                         LIMIT 1
                        """,
                (rs, i) -> new Llegada(rs.getLong("end_vid"), rs.getDouble("agg_cost")),
                origen, arreglo(destinos)).stream().findFirst();
    }

    /** Los puntos del camino, en orden, para pintarlo sobre el mapa. */
    private List<PuntoResponse> trazoDelCamino(Long origen, Long destino) {
        return jdbc.query("""
                        SELECT ST_Y(g.geom) AS latitud, ST_X(g.geom) AS longitud
                          FROM pgr_dijkstra(
                                 'SELECT id, origen AS source, destino AS target,
                                         costo AS cost, costo_reverso AS reverse_cost FROM calles',
                                 ?::bigint, ?::bigint, directed => true) d
                          JOIN calles c ON c.id = d.edge
                          CROSS JOIN LATERAL ST_DumpPoints(
                                 CASE WHEN c.origen = d.node THEN c.trazo
                                      ELSE ST_Reverse(c.trazo) END) g
                         ORDER BY d.seq, g.path
                        """,
                (rs, i) -> new PuntoResponse(rs.getDouble("latitud"), rs.getDouble("longitud")),
                origen, destino);
    }

    private static String arreglo(List<Long> nodos) {
        StringBuilder texto = new StringBuilder("{");
        for (int i = 0; i < nodos.size(); i++) {
            texto.append(i == 0 ? "" : ",").append(nodos.get(i));
        }
        return texto.append('}').toString();
    }

    /**
     * @param destino indice del candidato elegido en la lista recibida
     * @param metros  distancia real por calles hasta ese candidato
     * @param trazo   puntos del camino, del bus al punto de reincorporacion
     */
    public record Camino(int destino, double metros, List<PuntoResponse> trazo) {
    }

    private record Llegada(Long nodo, double metros) {
    }
}
