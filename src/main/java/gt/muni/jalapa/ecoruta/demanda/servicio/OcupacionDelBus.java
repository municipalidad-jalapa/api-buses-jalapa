package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.demanda.web.dto.OcupacionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Ocupacion del bus de una ruta para el mapa del pasajero.
 *
 * <p>Sale del conteo del piloto al cerrar paradas hoy. Sin ningun conteo hoy no
 * hay dato: se devuelve vacio y el pasajero ve "sin dato", nunca un 0 inventado.
 */
@Service
@RequiredArgsConstructor
public class OcupacionDelBus {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Optional<OcupacionDto> de(Long rutaId) {
        List<OcupacionDto> filas = jdbc.query("""
                        SELECT coalesce(sum(a.subieron), 0) AS subieron,
                               coalesce(sum(a.bajaron), 0)  AS bajaron,
                               max(a.marcada_en)            AS ultima,
                               (SELECT v.capacidad
                                  FROM vehiculos v
                                 WHERE v.ruta_id = ? AND v.activo
                                 LIMIT 1)                   AS capacidad
                          FROM paradas_atendidas a
                         WHERE a.ruta_id = ?
                           AND a.fecha_servicio = CURRENT_DATE
                           AND (a.subieron > 0 OR a.bajaron > 0)
                        """,
                (rs, i) -> {
                    Timestamp ultima = rs.getTimestamp("ultima");
                    if (ultima == null) {
                        return null;
                    }
                    int aBordo = Math.max(0, rs.getInt("subieron") - rs.getInt("bajaron"));
                    Integer capacidad = (Integer) rs.getObject("capacidad");
                    return new OcupacionDto(aBordo, capacidad, OcupacionDto.nivelDe(aBordo, capacidad),
                            ultima.toInstant());
                },
                rutaId, rutaId);
        return filas.stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
