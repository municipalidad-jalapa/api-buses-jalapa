package gt.muni.jalapa.ecoruta.seguridad.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * La ruta asignada a cada conductor ({@code usuarios.ruta_id}, HU-76).
 *
 * <p>El conductor se identifica por su {@code username} o por su
 * {@code firebase_uid}: el JWT de jornada que emite {@code AutenticacionDeConductor}
 * lleva el uid de Firebase como subject, no el username. Antes solo se buscaba
 * por username, y un conductor que entraba con su cuenta real recibia 403 al
 * marcar una parada (QA 5.3).
 */
@Repository
@RequiredArgsConstructor
public class ConductorRutaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean estaAsignadoARuta(
            String identidad,
            Long rutaId
    ) {
        return rutaDe(identidad).map(rutaId::equals).orElse(false);
    }

    /** La ruta del conductor activo con esa identidad, si tiene una. */
    public Optional<Long> rutaDe(String identidad) {
        List<Long> rutas = jdbc.queryForList("""
                        SELECT ruta_id
                          FROM usuarios
                         WHERE (username = :identidad OR firebase_uid = :identidad)
                           AND rol = 'CONDUCTOR'
                           AND activo = TRUE
                           AND ruta_id IS NOT NULL
                        """,
                new MapSqlParameterSource("identidad", identidad),
                Long.class);
        return rutas.stream().findFirst();
    }

    /**
     * El conductor elige la ruta que va a manejar. Mismo filtro que
     * {@link #rutaDe}: una cuenta que no es conductor activo no cambia nada.
     *
     * @return filas actualizadas; 0 si la identidad no es un conductor activo
     */
    public int asignar(String identidad, Long rutaId) {
        return jdbc.update("""
                        UPDATE usuarios
                           SET ruta_id = :rutaId
                         WHERE (username = :identidad OR firebase_uid = :identidad)
                           AND rol = 'CONDUCTOR'
                           AND activo = TRUE
                        """,
                new MapSqlParameterSource()
                        .addValue("identidad", identidad)
                        .addValue("rutaId", rutaId));
    }
}
