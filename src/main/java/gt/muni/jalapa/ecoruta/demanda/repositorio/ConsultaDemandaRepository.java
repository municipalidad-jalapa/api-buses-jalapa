package gt.muni.jalapa.ecoruta.demanda.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso de solo lectura a la demanda vigente por parada (SCRUM-275).
 *
 * <p>Deliberadamente sin entidad JPA: el ciclo completo de {@code Reserva}
 * pertenece a otras historias (SCRUM-306/307/308). Aqui solo se necesita el
 * conteo agrupado para armar el resumen de arranque.
 */
@Repository
@RequiredArgsConstructor
public class ConsultaDemandaRepository {

    private static final List<String> ESTADOS_VIGENTES = List.of("ACTIVA", "RENOVADA");

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Cuenta las reservas vigentes de todas las paradas en una sola consulta.
     *
     * <p>Solo cuentan {@code ACTIVA} y {@code RENOVADA}. El legado {@code ACTIVO}
     * y los estados terminales no entran.
     */
    public Map<Long, Long> contarReservasActivasPorParada(Collection<Long> paradaIds) {
        if (paradaIds == null || paradaIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT parada_id, COUNT(*) AS total
                FROM registros_espera
                WHERE parada_id IN (:paradaIds)
                  AND estado IN (:estados)
                GROUP BY parada_id
                """;

        var parametros = new MapSqlParameterSource()
                .addValue("paradaIds", paradaIds)
                .addValue("estados", ESTADOS_VIGENTES);

        Map<Long, Long> resultado = new HashMap<>();
        jdbc.query(sql, parametros, rs -> {
            resultado.put(rs.getLong("parada_id"), rs.getLong("total"));
        });
        return resultado;
    }
}
