package gt.muni.jalapa.ecoruta.seguridad.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConductorRutaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean estaAsignadoARuta(
            String username,
            Long rutaId
    ) {
        MapSqlParameterSource parametros =
                new MapSqlParameterSource()
                        .addValue("username", username)
                        .addValue("rutaId", rutaId);

        Boolean resultado =
                jdbc.queryForObject("""
                        SELECT EXISTS(
                            SELECT 1
                              FROM usuarios
                             WHERE username = :username
                               AND rol = 'CONDUCTOR'
                               AND activo = TRUE
                               AND ruta_id = :rutaId
                        )
                        """,
                        parametros,
                        Boolean.class);

        return Boolean.TRUE.equals(resultado);
    }
}