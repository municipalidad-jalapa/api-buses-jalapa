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
                                   AND a.fecha_servicio = CURRENT_DATE) AS atendida_en
                          FROM paradas p
                         WHERE p.ruta_id = ?
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
                rutaId);

        return new PanelConductorResponse(rutaId, rutaNombre, eta.estado(), Instant.now(reloj), paradas);
    }
}
