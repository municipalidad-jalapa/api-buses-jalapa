package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaParadaResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import gt.muni.jalapa.ecoruta.telemetria.repositorio.PosicionHistoricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Calcula el tiempo estimado de llegada del bus de una ruta a sus paradas
 * (SCRUM-166, HU Desarrollo-71).
 *
 * <p>Calculo propio sobre PostGIS, sin servicios externos de rutas ni de trafico:
 * la distancia se mide SIGUIENDO el trazado (ST_LineLocatePoint), no en linea
 * recta, y se divide entre la velocidad observada del bus en el tramo reciente.
 *
 * <p>Todo depende solo del {@code rutaId}: una segunda ruta con su propio bus no
 * requiere tocar el algoritmo.
 */
@Service
@RequiredArgsConstructor
public class CalculadorDeEta {

    /**
     * Una parada que el bus acaba de rebasar por menos que esto se considera
     * alcanzada, no pendiente de la vuelta siguiente. Absorbe el ruido del GPS
     * cuando el bus esta detenido en la parada.
     */
    static final double TOLERANCIA_PARADA_METROS = 30;

    private final JdbcTemplate jdbc;
    private final VehiculoRepository vehiculos;
    private final PosicionHistoricaRepository posiciones;
    private final EtaProperties propiedades;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public EtaCalculado calcular(Long rutaId) {
        List<ParadaEnTrazado> paradas = paradasDeLaRuta(rutaId);
        Instant ahora = reloj.instant();

        Optional<Vehiculo> vehiculo = vehiculos.findFirstByRutaIdAndActivoTrue(rutaId);
        Long vehiculoId = vehiculo.map(Vehiculo::getId).orElse(null);
        Optional<PosicionHistorica> ultima = vehiculo.flatMap(v ->
                posiciones.findFirstByVehiculoIdOrderByRegistradoEnDescIdDesc(v.getId()));

        if (ultima.isEmpty() || esVieja(ultima.get().getRegistradoEn(), ahora)) {
            return new EtaCalculado(sinEstimacion(rutaId, vehiculoId, ahora, paradas),
                    ultima.map(PosicionHistorica::getRegistradoEn).orElse(null));
        }

        PosicionHistorica posicion = ultima.get();
        Optional<Trazado> trazado = ubicarBus(rutaId, posicion);
        if (trazado.isEmpty()) {
            return new EtaCalculado(sinEstimacion(rutaId, vehiculoId, ahora, paradas),
                    posicion.getRegistradoEn());
        }

        OptionalDouble observada = velocidadObservada(vehiculoId, posicion.getRegistradoEn());
        double kmh = observada.orElse(propiedades.velocidadRespaldoKmh());
        List<EtaParadaResponse> etas = estimar(paradas, trazado.get(), kmh, observada.isPresent());

        return new EtaCalculado(new EtaRutaResponse(rutaId, vehiculoId, ahora, etas),
                posicion.getRegistradoEn());
    }

    public boolean esVieja(Instant registradoEn, Instant ahora) {
        return Duration.between(registradoEn, ahora).compareTo(propiedades.antiguedadMaxima()) > 0;
    }

    /**
     * El nucleo del calculo, sin base de datos.
     *
     * <p>Con el trazado cerrado (circuito) ninguna parada queda fuera: las que el
     * bus ya rebaso son las de la vuelta siguiente. Con un trazado abierto, las
     * rebasadas ya no estan pendientes y se omiten.
     */
    static List<EtaParadaResponse> estimar(List<ParadaEnTrazado> paradas, Trazado trazado,
                                           double kmh, boolean confiable) {
        double metrosPorMinuto = kmh * 1000 / 60;
        return paradas.stream()
                .map(parada -> {
                    Double metros = metrosPendientes(parada.fraccion(), trazado);
                    if (metros == null) {
                        return null;
                    }
                    int minutos = (int) Math.ceil(metros / metrosPorMinuto);
                    return new EtaParadaResponse(parada.paradaId(), parada.orden(), minutos, confiable);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Metros sobre el trazado hasta la parada, o null si ya no esta pendiente. */
    static Double metrosPendientes(double fraccionParada, Trazado trazado) {
        double adelante = (fraccionParada - trazado.fraccionBus()) * trazado.largoMetros();
        if (adelante >= 0) {
            return adelante;
        }
        if (-adelante <= TOLERANCIA_PARADA_METROS) {
            return 0d;
        }
        return trazado.circuito() ? trazado.largoMetros() + adelante : null;
    }

    private OptionalDouble velocidadObservada(Long vehiculoId, Instant ultimaLectura) {
        return posiciones
                .findByVehiculoIdAndRegistradoEnGreaterThanEqualOrderByRegistradoEnDescIdDesc(
                        vehiculoId,
                        ultimaLectura.minus(propiedades.ventanaVelocidad()),
                        PageRequest.of(0, propiedades.muestrasVelocidad()))
                .stream()
                .map(PosicionHistorica::getVelocidadKmh)
                .filter(Objects::nonNull)
                .filter(kmh -> kmh >= propiedades.velocidadMinimaKmh())
                .mapToDouble(Double::doubleValue)
                .average();
    }

    private List<ParadaEnTrazado> paradasDeLaRuta(Long rutaId) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM rutas WHERE id = ?)", Boolean.class, rutaId);
        if (!Boolean.TRUE.equals(existe)) {
            throw new RecursoNoEncontradoException("Ruta", rutaId);
        }
        // Sin trazado la fraccion viene null; solo se usa si hay trazado.
        return jdbc.query("""
                        SELECT p.id, p.orden,
                               COALESCE(ST_LineLocatePoint(r.trazado, p.ubicacion), 0) AS fraccion
                          FROM paradas p
                          JOIN rutas r ON r.id = p.ruta_id
                         WHERE r.id = ?
                         ORDER BY p.orden
                        """,
                (rs, i) -> new ParadaEnTrazado(rs.getLong("id"), rs.getInt("orden"), rs.getDouble("fraccion")),
                rutaId);
    }

    /** Donde cae el bus sobre el trazado. Vacio si la ruta no tiene trazado. */
    private Optional<Trazado> ubicarBus(Long rutaId, PosicionHistorica posicion) {
        // Metros con geography, como pide el ADR-007. El punto va en (lon, lat).
        return jdbc.query("""
                        SELECT ST_LineLocatePoint(trazado, ST_SetSRID(ST_MakePoint(?, ?), 4326)) AS fraccion,
                               ST_Length(trazado::geography) AS largo,
                               ST_IsClosed(trazado) AS circuito
                          FROM rutas
                         WHERE id = ? AND trazado IS NOT NULL
                        """,
                (rs, i) -> new Trazado(rs.getDouble("fraccion"), rs.getDouble("largo"), rs.getBoolean("circuito")),
                posicion.getUbicacion().getX(), posicion.getUbicacion().getY(), rutaId)
                .stream().findFirst();
    }

    private static EtaRutaResponse sinEstimacion(Long rutaId, Long vehiculoId, Instant ahora,
                                                 List<ParadaEnTrazado> paradas) {
        return new EtaRutaResponse(rutaId, vehiculoId, ahora, paradas.stream()
                .map(p -> EtaParadaResponse.noDisponible(p.paradaId(), p.orden()))
                .toList());
    }

    record ParadaEnTrazado(Long paradaId, int orden, double fraccion) {
    }

    record Trazado(double fraccionBus, double largoMetros, boolean circuito) {
    }

    /**
     * @param posicionEn instante de la posicion usada, para revalidar su
     *                   antiguedad al servir el resultado desde la cache
     */
    public record EtaCalculado(EtaRutaResponse respuesta, Instant posicionEn) {
    }
}
