package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.AccesoDenegadoException;
import gt.muni.jalapa.ecoruta.demanda.web.dto.PanelConductorResponse;
import gt.muni.jalapa.ecoruta.eta.servicio.EtaService;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaParadaResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import gt.muni.jalapa.ecoruta.seguridad.repositorio.ConductorRutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma el panel del conductor (QA 4.3 y 5.3: se perdio en la migracion).
 *
 * <p>La ruta no se elige: sale de la asignacion del conductor autenticado
 * ({@code usuarios.ruta_id}). Sin ruta asignada responde 403, igual que marcar
 * una parada ajena.
 */
@Service
@RequiredArgsConstructor
public class PanelConductorService {

    private final ConductorRutaRepository conductorRuta;
    private final EtaService etas;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public PanelConductorResponse armar(String conductor) {
        Long rutaId = conductorRuta.rutaDe(conductor)
                .orElseThrow(() -> new AccesoDenegadoException(
                        "No tenés una ruta asignada. Pedile a la Municipalidad que te asigne una."));

        String rutaNombre = jdbc.queryForObject(
                "SELECT nombre FROM rutas WHERE id = ?", String.class, rutaId);

        EtaRutaResponse eta = etas.consultar(rutaId);
        Map<Long, EtaParadaResponse> etaPorParada = eta.paradas().stream()
                .collect(Collectors.toMap(EtaParadaResponse::paradaId, Function.identity(), (a, b) -> a));

        // Vuelta que se muestra: la de hoy en curso; si ya se cerraron todas sus
        // paradas, la siguiente, con todo pendiente otra vez.
        int vuelta = jdbc.queryForObject("""
                        SELECT CASE
                                 WHEN (SELECT count(*) FROM paradas WHERE ruta_id = ? AND retirada_en IS NULL) > 0
                                  AND (SELECT count(DISTINCT parada_id) FROM paradas_atendidas
                                        WHERE ruta_id = ? AND fecha_servicio = CURRENT_DATE AND vuelta = v.actual)
                                      >= (SELECT count(*) FROM paradas WHERE ruta_id = ? AND retirada_en IS NULL)
                                 THEN v.actual + 1
                                 ELSE v.actual
                               END
                          FROM (SELECT coalesce(max(vuelta), 1) AS actual
                                  FROM paradas_atendidas
                                 WHERE ruta_id = ? AND fecha_servicio = CURRENT_DATE) v
                        """,
                Integer.class, rutaId, rutaId, rutaId, rutaId);

        var paradas = jdbc.query("""
                        SELECT p.id,
                               p.nombre,
                               p.orden,
                               (SELECT count(*)
                                  FROM registros_espera r
                                 WHERE r.parada_id = p.id
                                   AND r.estado IN ('ACTIVA', 'RENOVADA')
                                   AND r.expira_en > now()) AS reservas,
                               (SELECT max(a.marcada_en)
                                  FROM paradas_atendidas a
                                 WHERE a.parada_id = p.id
                                   AND a.ruta_id = p.ruta_id
                                   AND a.fecha_servicio = CURRENT_DATE
                                   AND a.vuelta = ?) AS atendida_en
                          FROM paradas p
                         WHERE p.ruta_id = ?
                           AND p.retirada_en IS NULL
                         ORDER BY p.orden
                        """,
                (rs, i) -> {
                    long paradaId = rs.getLong("id");
                    EtaParadaResponse suEta = etaPorParada.get(paradaId);
                    Timestamp atendida = rs.getTimestamp("atendida_en");
                    return new PanelConductorResponse.Parada(
                            paradaId,
                            rs.getString("nombre"),
                            rs.getInt("orden"),
                            rs.getInt("reservas"),
                            suEta != null ? suEta.minutos() : null,
                            suEta != null && suEta.confiable(),
                            atendida != null ? atendida.toInstant() : null);
                },
                vuelta, rutaId);

        // Lo que conto el piloto al cerrar cada parada hoy (botones Subio y Bajo).
        int[] conteo = jdbc.queryForObject("""
                        SELECT coalesce(sum(subieron), 0) AS subieron,
                               coalesce(sum(bajaron), 0)  AS bajaron
                          FROM paradas_atendidas
                         WHERE ruta_id = ?
                           AND fecha_servicio = CURRENT_DATE
                        """,
                (rs, i) -> new int[] {rs.getInt("subieron"), rs.getInt("bajaron")},
                rutaId);
        int aBordo = Math.max(0, conteo[0] - conteo[1]);

        return new PanelConductorResponse(rutaId, rutaNombre, eta.estado(), Instant.now(reloj), paradas,
                conteo[0], conteo[1], aBordo, vuelta);
    }
}
