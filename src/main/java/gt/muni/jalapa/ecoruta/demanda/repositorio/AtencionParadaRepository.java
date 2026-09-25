package gt.muni.jalapa.ecoruta.demanda.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class AtencionParadaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void registrar(
            Long rutaId,
            Long paradaId,
            String conductor,
            Instant marcadaEn,
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
                        .addValue("subieron", subieron)
                        .addValue("bajaron", bajaron);

        jdbc.update("""
                INSERT INTO paradas_atendidas (
                    ruta_id,
                    parada_id,
                    conductor_username,
                    fecha_servicio,
                    marcada_en,
                    subieron,
                    bajaron
                )
                VALUES (
                    :rutaId,
                    :paradaId,
                    :conductor,
                    CURRENT_DATE,
                    :marcadaEn,
                    :subieron,
                    :bajaron
                )
                """,
                parametros);
    }
}