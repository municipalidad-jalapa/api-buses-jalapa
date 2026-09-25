package gt.muni.jalapa.ecoruta.demanda.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AtencionParadaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** La vuelta en curso hoy en la ruta: la mas alta con alguna parada cerrada, o 1. */
    public int vueltaActual(Long rutaId) {
        Integer vuelta = jdbc.queryForObject("""
                SELECT coalesce(max(vuelta), 1)
                  FROM paradas_atendidas
                 WHERE ruta_id = :rutaId
                   AND fecha_servicio = CURRENT_DATE
                """,
                new MapSqlParameterSource("rutaId", rutaId),
                Integer.class);
        return vuelta == null ? 1 : vuelta;
    }

    /** Cuando se cerro la parada en esa vuelta de hoy, si ya se cerro. */
    public Optional<Instant> cerradaEn(Long rutaId, Long paradaId, int vuelta) {
        return jdbc.query("""
                        SELECT marcada_en
                          FROM paradas_atendidas
                         WHERE ruta_id = :rutaId
                           AND parada_id = :paradaId
                           AND fecha_servicio = CURRENT_DATE
                           AND vuelta = :vuelta
                        """,
                        new MapSqlParameterSource()
                                .addValue("rutaId", rutaId)
                                .addValue("paradaId", paradaId)
                                .addValue("vuelta", vuelta),
                        (rs, i) -> rs.getTimestamp("marcada_en").toInstant())
                .stream()
                .findFirst();
    }

    public void registrar(
            Long rutaId,
            Long paradaId,
            String conductor,
            Instant marcadaEn,
            int vuelta,
            int subieron,
            int bajaron
    ) {

        MapSqlParameterSource parametros =
                new MapSqlParameterSource()
                        .addValue("rutaId", rutaId)
                        .addValue("paradaId", paradaId)
                        .addValue("conductor", conductor)
                        .addValue(
                                "marcadaEn",
                                Timestamp.from(marcadaEn)
                        )
                        .addValue("vuelta", vuelta)
                        .addValue("subieron", subieron)
                        .addValue("bajaron", bajaron);

        jdbc.update("""
                INSERT INTO paradas_atendidas (
                    ruta_id,
                    parada_id,
                    conductor_username,
                    fecha_servicio,
                    marcada_en,
                    vuelta,
                    subieron,
                    bajaron
                )
                VALUES (
                    :rutaId,
                    :paradaId,
                    :conductor,
                    CURRENT_DATE,
                    :marcadaEn,
                    :vuelta,
                    :subieron,
                    :bajaron
                )
                """,
                parametros);
    }
}
