package gt.muni.jalapa.ecoruta.panel.servicio;

import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.AbordajesPorPeriodo;
import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.ConteoDeAbordajesResponse;
import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.Granularidad;
import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.ConteoPorGrupo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuantos pasajeros subieron, por ruta, por vehiculo y por periodo
 * (SCRUM-26, bloque F, criterio 1).
 *
 * <p>Se cuenta <b>lo que marco el piloto</b>, no lo que respondio el pasajero:
 * el dato del piloto es el que prevalece (HU-57, y el bloque E lo reutiliza).
 * Una reserva que el pasajero dijo haber abordado pero el piloto nunca marco no
 * entra en el conteo; si se contaran las dos fuentes, el numero del panel
 * dejaria de ser comparable entre rutas.
 *
 * <p>El vehiculo es el que llevaba la ruta en ese momento, guardado en la
 * reserva por el abordaje. Si no quedo registrado, la fila cuenta igual en la
 * ruta pero no se atribuye a ninguna unidad.
 */
@Service
@RequiredArgsConstructor
public class ConteoDeAbordajes {

    private final JdbcTemplate jdbc;

    /**
     * @param rutaId     null = todas las rutas
     * @param vehiculoId null = todos los buses
     * @param desde      inclusive; null = sin limite inferior
     * @param hasta      exclusive; null = sin limite superior
     */
    public record Filtros(Long rutaId, Long vehiculoId, Instant desde, Instant hasta,
                          Granularidad granularidad) {
    }

    @Transactional(readOnly = true)
    public ConteoDeAbordajesResponse contar(Filtros filtros) {
        List<Object> parametros = new ArrayList<>();
        String donde = condiciones(filtros, parametros);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM registros_espera r JOIN paradas p ON p.id = r.parada_id" + donde,
                Long.class, parametros.toArray());

        List<ConteoPorGrupo> porRuta = jdbc.query("""
                        SELECT p.ruta_id AS id, ru.nombre AS nombre, count(*) AS abordajes
                          FROM registros_espera r
                          JOIN paradas p ON p.id = r.parada_id
                          JOIN rutas ru ON ru.id = p.ruta_id
                        """ + donde + " GROUP BY p.ruta_id, ru.nombre ORDER BY ru.nombre",
                (rs, i) -> new ConteoPorGrupo(rs.getLong("id"), rs.getString("nombre"),
                        rs.getLong("abordajes")),
                parametros.toArray());

        List<ConteoPorGrupo> porVehiculo = jdbc.query("""
                        SELECT v.id AS id, v.identificador AS nombre, count(*) AS abordajes
                          FROM registros_espera r
                          JOIN paradas p ON p.id = r.parada_id
                          JOIN vehiculos v ON v.ruta_id = p.ruta_id
                        """ + donde + " GROUP BY v.id, v.identificador ORDER BY v.identificador",
                (rs, i) -> new ConteoPorGrupo(rs.getLong("id"), rs.getString("nombre"),
                        rs.getLong("abordajes")),
                parametros.toArray());

        Granularidad granularidad = filtros.granularidad() == null
                ? Granularidad.DIA : filtros.granularidad();
        List<AbordajesPorPeriodo> porPeriodo = jdbc.query("""
                        SELECT date_trunc(?, r.abordaje_en) AS periodo, count(*) AS abordajes
                          FROM registros_espera r
                          JOIN paradas p ON p.id = r.parada_id
                        """ + donde + " GROUP BY periodo ORDER BY periodo",
                (rs, i) -> new AbordajesPorPeriodo(rs.getTimestamp("periodo").toInstant(),
                        rs.getLong("abordajes")),
                conGranularidad(granularidad, parametros));

        return new ConteoDeAbordajesResponse(total == null ? 0 : total, granularidad,
                porRuta, porVehiculo, porPeriodo);
    }

    /** El primer parametro del agrupado por periodo es la unidad de date_trunc. */
    private static Object[] conGranularidad(Granularidad granularidad, List<Object> parametros) {
        List<Object> todos = new ArrayList<>();
        todos.add(granularidad.unidadSql());
        todos.addAll(parametros);
        return todos.toArray();
    }

    private static String condiciones(Filtros filtros, List<Object> parametros) {
        // Solo lo que marco el piloto: subio = true y la fuente es el conductor.
        StringBuilder donde = new StringBuilder(
                " WHERE r.subio IS TRUE AND r.abordaje_fuente = 'CONDUCTOR' AND r.abordaje_en IS NOT NULL");
        if (filtros.rutaId() != null) {
            donde.append(" AND p.ruta_id = ?");
            parametros.add(filtros.rutaId());
        }
        if (filtros.vehiculoId() != null) {
            donde.append(" AND p.ruta_id = (SELECT ruta_id FROM vehiculos WHERE id = ?)");
            parametros.add(filtros.vehiculoId());
        }
        if (filtros.desde() != null) {
            donde.append(" AND r.abordaje_en >= ?");
            parametros.add(Timestamp.from(filtros.desde()));
        }
        if (filtros.hasta() != null) {
            donde.append(" AND r.abordaje_en < ?");
            parametros.add(Timestamp.from(filtros.hasta()));
        }
        return donde.toString();
    }
}
