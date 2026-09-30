package gt.muni.jalapa.ecoruta.demanda.servicio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cuanta gente cabe en el bus de una ruta y cuanta lleva hoy (QA, panel del
 * conductor: "Bajo" no puede dejar el contador en negativo y "Subio" no puede
 * pasar la capacidad del bus).
 */
@Service
@RequiredArgsConstructor
public class CargaDelBus {

    private final JdbcTemplate jdbc;
    private final BusProperties bus;

    /**
     * Capacidad del bus activo de la ruta. Si la Municipalidad no la cargo (o la
     * ruta no tiene bus activo) se usa {@code ecoruta.bus.capacidad-por-defecto}.
     */
    public int capacidadDe(Long rutaId) {
        List<Integer> capacidades = jdbc.query("""
                        SELECT v.capacidad
                          FROM vehiculos v
                         WHERE v.ruta_id = ?
                           AND v.activo
                         ORDER BY v.id
                         LIMIT 1
                        """,
                (rs, i) -> (Integer) rs.getObject("capacidad"),
                rutaId);
        Integer capacidad = capacidades.isEmpty() ? null : capacidades.get(0);
        return capacidad != null && capacidad > 0 ? capacidad : bus.capacidadPorDefecto();
    }

    /**
     * Subieron menos bajaron hoy segun el conteo del piloto al cerrar paradas;
     * nunca negativo. Es el mismo "a bordo" que muestra el panel.
     */
    public int aBordoHoy(Long rutaId) {
        Integer aBordo = jdbc.queryForObject("""
                        SELECT greatest(0, coalesce(sum(subieron), 0) - coalesce(sum(bajaron), 0))
                          FROM paradas_atendidas
                         WHERE ruta_id = ?
                           AND fecha_servicio = CURRENT_DATE
                        """,
                Integer.class, rutaId);
        return aBordo == null ? 0 : aBordo;
    }
}
