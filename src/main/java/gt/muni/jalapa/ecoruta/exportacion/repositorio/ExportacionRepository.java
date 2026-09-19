package gt.muni.jalapa.ecoruta.exportacion.repositorio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Consultas agregadas para la exportacion del servicio (HU Desarrollo-86).
 *
 * <p>Solo lectura y SIEMPRE agregada: cada fila resume muchas reservas o muchas
 * lecturas GPS. Ninguna consulta selecciona {@code dispositivo_id}, el id de la
 * reserva, el token de notificaciones ni el usuario del conductor, asi que el dato
 * que identifica a un pasajero no llega ni a la memoria del proceso. Es la garantia
 * del criterio "no expone datos que identifiquen a un pasajero": no depende de que
 * quien arma el libro se acuerde de omitir una columna.
 *
 * <p>Sin entidades JPA, igual que {@code ConsultaDemandaRepository}: son consultas
 * de reporte con {@code GROUP BY}, no el ciclo de vida de una reserva.
 */
@Repository
@RequiredArgsConstructor
public class ExportacionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Demanda por dia y parada. Las reservas se ubican en el dia (de {@code zona}) en
     * que se crearon; {@code estado} es el que tienen al momento de exportar.
     *
     * @param desde inclusive
     * @param hasta exclusivo
     */
    public List<FilaDemanda> demandaPorDiaYParada(Instant desde, Instant hasta, ZoneId zona) {
        String sql = """
                SELECT (r.creado_en AT TIME ZONE :zona)::date AS fecha,
                       ru.id AS ruta_id,
                       ru.nombre AS ruta,
                       p.orden AS orden_parada,
                       p.nombre AS parada,
                       COUNT(*) AS reservas,
                       COUNT(*) FILTER (WHERE r.estado = 'ABORDO') AS abordaron,
                       COUNT(*) FILTER (WHERE r.estado = 'CANCELADA') AS canceladas,
                       COUNT(*) FILTER (WHERE r.estado = 'EXPIRADA') AS expiradas,
                       COUNT(*) FILTER (WHERE r.estado IN ('ACTIVA', 'RENOVADA')) AS vigentes,
                       COUNT(*) FILTER (WHERE r.pasajero_declaro_no_abordo) AS declararon_no_abordo
                  FROM registros_espera r
                  JOIN paradas p ON p.id = r.parada_id
                  JOIN rutas ru ON ru.id = p.ruta_id
                 WHERE r.creado_en >= :desde
                   AND r.creado_en < :hasta
                 GROUP BY 1, ru.id, ru.nombre, p.id, p.orden, p.nombre
                 ORDER BY 1, ru.id, p.orden
                """;

        return jdbc.query(sql, parametros(desde, hasta, zona),
                (rs, fila) -> new FilaDemanda(
                        rs.getObject("fecha", LocalDate.class),
                        rs.getLong("ruta_id"),
                        rs.getString("ruta"),
                        rs.getInt("orden_parada"),
                        rs.getString("parada"),
                        rs.getLong("reservas"),
                        rs.getLong("abordaron"),
                        rs.getLong("canceladas"),
                        rs.getLong("expiradas"),
                        rs.getLong("vigentes"),
                        rs.getLong("declararon_no_abordo")));
    }

    /**
     * Recorridos por dia y bus, a partir de las lecturas GPS.
     *
     * <p>La distancia suma los tramos entre lecturas consecutivas del mismo bus en
     * el mismo dia, midiendo sobre el elipsoide (geography). Un tramo cuyo intervalo
     * supera {@code saltoMaximoSegundos} no suma: es un hueco de datos. Como el
     * GPS de un bus detenido tiembla, la cifra es una estimacion y no un odometro.
     *
     * @param desde inclusive
     * @param hasta exclusivo
     */
    public List<FilaRecorrido> recorridosPorDiaYBus(Instant desde, Instant hasta, ZoneId zona,
                                                    int saltoMaximoSegundos) {
        String sql = """
                WITH lecturas AS (
                    SELECT p.vehiculo_id,
                           (p.registrado_en AT TIME ZONE :zona)::date AS fecha,
                           p.id,
                           p.registrado_en,
                           p.velocidad_kmh,
                           p.ubicacion
                      FROM posiciones_historicas p
                     WHERE p.vehiculo_id IS NOT NULL
                       AND p.registrado_en >= :desde
                       AND p.registrado_en < :hasta
                ),
                tramos AS (
                    SELECT l.*,
                           LAG(l.ubicacion) OVER dia AS ubicacion_previa,
                           LAG(l.registrado_en) OVER dia AS registrado_previo
                      FROM lecturas l
                    WINDOW dia AS (PARTITION BY l.vehiculo_id, l.fecha ORDER BY l.registrado_en, l.id)
                ),
                por_bus AS (
                    SELECT t.fecha,
                           t.vehiculo_id,
                           COUNT(*) AS lecturas,
                           MIN(t.registrado_en) AS primera_lectura,
                           MAX(t.registrado_en) AS ultima_lectura,
                           COALESCE(SUM(
                               CASE WHEN t.ubicacion_previa IS NOT NULL
                                     AND EXTRACT(EPOCH FROM (t.registrado_en - t.registrado_previo)) <= :salto
                                    THEN ST_Distance(t.ubicacion::geography, t.ubicacion_previa::geography)
                                    ELSE 0 END), 0) AS distancia_metros,
                           AVG(t.velocidad_kmh) FILTER (WHERE t.velocidad_kmh > 0) AS velocidad_promedio_kmh
                      FROM tramos t
                     GROUP BY t.fecha, t.vehiculo_id
                ),
                atendidas AS (
                    SELECT pa.ruta_id,
                           (pa.marcada_en AT TIME ZONE :zona)::date AS fecha,
                           COUNT(*) AS paradas_atendidas
                      FROM paradas_atendidas pa
                     WHERE pa.marcada_en >= :desde
                       AND pa.marcada_en < :hasta
                     GROUP BY 1, 2
                )
                SELECT b.fecha,
                       v.identificador AS bus,
                       v.placa,
                       ru.id AS ruta_id,
                       ru.nombre AS ruta,
                       b.lecturas,
                       b.primera_lectura,
                       b.ultima_lectura,
                       b.distancia_metros,
                       b.velocidad_promedio_kmh,
                       COALESCE(a.paradas_atendidas, 0) AS paradas_atendidas
                  FROM por_bus b
                  JOIN vehiculos v ON v.id = b.vehiculo_id
                  LEFT JOIN rutas ru ON ru.id = v.ruta_id
                  LEFT JOIN atendidas a ON a.ruta_id = v.ruta_id AND a.fecha = b.fecha
                 ORDER BY b.fecha, v.identificador
                """;

        return jdbc.query(sql, parametros(desde, hasta, zona).addValue("salto", saltoMaximoSegundos),
                (rs, fila) -> new FilaRecorrido(
                        rs.getObject("fecha", LocalDate.class),
                        rs.getString("bus"),
                        rs.getString("placa"),
                        rs.getObject("ruta_id", Long.class),
                        rs.getString("ruta"),
                        rs.getLong("lecturas"),
                        rs.getTimestamp("primera_lectura").toInstant(),
                        rs.getTimestamp("ultima_lectura").toInstant(),
                        rs.getDouble("distancia_metros"),
                        rs.getObject("velocidad_promedio_kmh", Double.class),
                        rs.getLong("paradas_atendidas")));
    }

    private static MapSqlParameterSource parametros(Instant desde, Instant hasta, ZoneId zona) {
        return new MapSqlParameterSource()
                .addValue("desde", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC))
                .addValue("hasta", OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC))
                .addValue("zona", zona.getId());
    }

    /** Un dia en una parada. Todo son conteos: no hay ningun dato de una persona. */
    public record FilaDemanda(LocalDate fecha, Long rutaId, String ruta, int ordenParada, String parada,
                              long reservas, long abordaron, long canceladas, long expiradas, long vigentes,
                              long declararonNoAbordo) {
    }

    /**
     * Un dia de un bus. {@code rutaId} y {@code ruta} son null si el bus no tiene ruta
     * asignada hoy; {@code velocidadPromedioKmh}, null si nunca se movio ese dia.
     */
    public record FilaRecorrido(LocalDate fecha, String bus, String placa, Long rutaId, String ruta,
                                long lecturas, Instant primeraLectura, Instant ultimaLectura,
                                double distanciaMetros, Double velocidadPromedioKmh, long paradasAtendidas) {
    }
}
