package gt.muni.jalapa.ecoruta.demanda.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de demanda.
 *
 * Incluye:
 * - demanda vigente por parada;
 * - demanda historica por parada y franja horaria.
 */
@Repository
@RequiredArgsConstructor
public class ConsultaDemandaRepository {

    private static final List<String> ESTADOS_VIGENTES =
            List.of("ACTIVA", "RENOVADA");

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Cuenta las reservas vigentes de todas las paradas.
     */
    public Map<Long, Long> contarReservasActivasPorParada(
            Collection<Long> paradaIds
    ) {

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
            resultado.put(
                    rs.getLong("parada_id"),
                    rs.getLong("total")
            );
        });

        return resultado;
    }

    /**
     * HU-85.
     *
     * Cuenta las reservas creadas en una parada,
     * agrupadas por hora local de Guatemala.
     */
    public Map<Integer, Long> contarHistoricoPorHora(
            Long paradaId,
            Instant desde,
            Instant hasta
    ) {

        String sql = """
                SELECT
                    EXTRACT(
                        HOUR FROM (
                            creado_en AT TIME ZONE 'America/Guatemala'
                        )
                    )::INTEGER AS hora,
                    COUNT(*) AS total
                FROM registros_espera
                WHERE parada_id = :paradaId
                  AND creado_en >= :desde
                  AND creado_en < :hasta
                GROUP BY 1
                ORDER BY 1
                """;

        var parametros = new MapSqlParameterSource()
                .addValue("paradaId", paradaId)
                .addValue("desde", Timestamp.from(desde))
                .addValue("hasta", Timestamp.from(hasta));

        Map<Integer, Long> resultado = new HashMap<>();

        jdbc.query(sql, parametros, rs -> {
            resultado.put(
                    rs.getInt("hora"),
                    rs.getLong("total")
            );
        });

        return resultado;
    }
}
