package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.calles.RedDeCalles;
import gt.muni.jalapa.ecoruta.calles.RedDeCallesProperties;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.web.dto.DesvioResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EstadoDelBus;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Calcula el tiempo estimado de llegada del bus de una ruta a sus paradas
 * (SCRUM-166, HU Desarrollo-71).
 *
 * <p>Calculo propio sobre PostGIS, sin servicios externos de rutas ni de trafico:
 * la distancia se mide SIGUIENDO el trazado (ST_LineLocatePoint), no en linea
 * recta, y se divide entre la velocidad observada del bus en el tramo reciente.
 * Encima de eso, para acercarse a lo que realmente pasa en la calle:
 *
 * <ul>
 *   <li>si el equipo no reporta velocidad, se deduce de posiciones seguidas;</li>
 *   <li>cada parada intermedia suma su tiempo de espera, mayor si tiene reservas
 *       (CP-ETA-03 de HU-84);</li>
 *   <li>detenido fuera de parada demasiado tiempo, no se proyecta un numero
 *       optimista (CP-ETA-05);</li>
 *   <li>fuera del trazado, el ETA se recalcula por el camino de reincorporacion
 *       y se devuelve ese recorrido (CP-ETA-10).</li>
 * </ul>
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

    /** Una velocidad deducida mayor es un salto del GPS, no el bus. */
    static final double VELOCIDAD_MAXIMA_CREIBLE_KMH = 90;

    /** Hasta donde se mira atras para detencion y desvio. */
    private static final Duration HISTORIAL = Duration.ofMinutes(30);
    private static final int MAXIMO_LECTURAS = 500;

    private final JdbcTemplate jdbc;
    private final VehiculoRepository vehiculos;
    private final PosicionHistoricaRepository posiciones;
    private final EtaProperties propiedades;
    private final RedDeCalles calles;
    private final RedDeCallesProperties callesPropiedades;
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
            return new EtaCalculado(sinEstimacion(rutaId, vehiculoId, ahora, EstadoDelBus.SIN_DATOS, paradas),
                    ultima.map(PosicionHistorica::getRegistradoEn).orElse(null));
        }

        PosicionHistorica posicion = ultima.get();
        Instant posicionEn = posicion.getRegistradoEn();
        Optional<Trazado> ubicado = ubicarBus(rutaId, posicion);
        if (ubicado.isEmpty()) {
            return new EtaCalculado(sinEstimacion(rutaId, vehiculoId, ahora, EstadoDelBus.SIN_DATOS, paradas),
                    posicionEn);
        }
        Trazado trazado = ubicado.get();

        List<Lectura> lecturas = lecturasRecientes(vehiculoId, posicionEn);
        List<Double> velocidades = velocidadesEfectivas(lecturas);
        Duration detenido = tiempoDetenido(lecturas, velocidades, propiedades.velocidadMinimaKmh());
        Lectura actual = lecturas.getFirst();
        Optional<ParadaEnTrazado> paradaActual = paradaCercana(paradas, actual, propiedades.radioParadaMetros());

        // CP-ETA-05: parado fuera de parada mas del maximo (averia, fin de turno).
        if (paradaActual.isEmpty() && detenido.compareTo(propiedades.detenidoMaximo()) >= 0) {
            return new EtaCalculado(sinEstimacion(rutaId, vehiculoId, ahora,
                    EstadoDelBus.DETENIDO_FUERA_DE_PARADA, paradas), posicionEn);
        }

        OptionalDouble observada = velocidadObservada(lecturas, velocidades, posicionEn);
        double kmh = observada.orElse(propiedades.velocidadRespaldoKmh());
        boolean confiable = observada.isPresent();

        EstadoDelBus estado = EstadoDelBus.EN_RUTA;
        int esperaRestante = 0;
        if (paradaActual.isPresent() && !detenido.isZero()) {
            estado = EstadoDelBus.DETENIDO_EN_PARADA;
            esperaRestante = (int) Math.max(0,
                    espera(paradaActual.get(), propiedades) - detenido.toSeconds());
        }

        Reincorporacion reincorporacion = null;
        DesvioResponse desvio = null;
        if (trazado.metrosAlTrazado() > propiedades.desvioMetros()) {
            estado = EstadoDelBus.EN_DESVIO;
            confiable = false;   // el camino por calles es una estimacion
            DesvioCalculado calculado = calcularDesvio(rutaId, vehiculoId, posicion, trazado, lecturas);
            reincorporacion = calculado.reincorporacion();
            desvio = calculado.respuesta();
        }

        List<EtaParadaResponse> etas = estimar(paradas, trazado, reincorporacion, kmh, confiable,
                esperaRestante, propiedades);
        return new EtaCalculado(new EtaRutaResponse(rutaId, vehiculoId, ahora, estado, desvio, etas),
                posicionEn);
    }

    public boolean esVieja(Instant registradoEn, Instant ahora) {
        return Duration.between(registradoEn, ahora).compareTo(propiedades.antiguedadMaxima()) > 0;
    }

    // -----------------------------------------------------------------------
    // Nucleo del calculo, sin base de datos
    // -----------------------------------------------------------------------

    /**
     * Minutos a cada parada. Tiempo de viaje por la distancia pendiente mas la
     * espera de cada parada intermedia, y lo que le falte al bus en la parada
     * donde esta detenido.
     *
     * @param reincorporacion null si el bus va sobre el trazado
     */
    static List<EtaParadaResponse> estimar(List<ParadaEnTrazado> paradas, Trazado trazado,
                                           Reincorporacion reincorporacion, double kmh,
                                           boolean confiable, int esperaRestanteSegundos,
                                           EtaProperties propiedades) {
        double metrosPorSegundo = kmh / 3.6;
        List<Double> distancias = paradas.stream()
                .map(p -> reincorporacion == null
                        ? metrosPendientes(p.fraccion(), trazado)
                        : metrosConDesvio(p.fraccion(), trazado, reincorporacion))
                .toList();

        List<EtaParadaResponse> etas = new ArrayList<>();
        for (int i = 0; i < paradas.size(); i++) {
            ParadaEnTrazado parada = paradas.get(i);
            Double metros = distancias.get(i);
            if (metros == null) {
                continue;   // ya rebasada en un trazado abierto
            }
            if (metros.isNaN()) {
                // El desvio la salta en un trazado abierto: no se sabe si pasara.
                etas.add(EtaParadaResponse.noDisponible(parada.paradaId(), parada.orden()));
                continue;
            }
            if (metros <= TOLERANCIA_PARADA_METROS) {
                etas.add(new EtaParadaResponse(parada.paradaId(), parada.orden(), 0, confiable));
                continue;
            }
            double segundos = metros / metrosPorSegundo + esperaRestanteSegundos;
            for (int j = 0; j < paradas.size(); j++) {
                Double intermedia = distancias.get(j);
                if (j != i && intermedia != null && !intermedia.isNaN()
                        && intermedia > TOLERANCIA_PARADA_METROS && intermedia < metros) {
                    segundos += espera(paradas.get(j), propiedades);
                }
            }
            etas.add(new EtaParadaResponse(parada.paradaId(), parada.orden(),
                    (int) Math.ceil(segundos / 60), confiable));
        }
        return etas;
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

    /**
     * Metros hasta la parada yendo primero al punto de reincorporacion.
     *
     * @return null si ya se rebaso (trazado abierto), NaN si el desvio la salta
     *         (trazado abierto)
     */
    static Double metrosConDesvio(double fraccionParada, Trazado trazado, Reincorporacion desvio) {
        double largo = trazado.largoMetros();
        double hastaVolver = desvio.metrosHastaReincorporar();
        if (fraccionParada >= desvio.fraccionReincorporacion()) {
            return hastaVolver + (fraccionParada - desvio.fraccionReincorporacion()) * largo;
        }
        if (trazado.circuito()) {
            return hastaVolver + largo - (desvio.fraccionReincorporacion() - fraccionParada) * largo;
        }
        return fraccionParada >= desvio.fraccionSalida() ? Double.NaN : null;
    }

    static int espera(ParadaEnTrazado parada, EtaProperties propiedades) {
        return parada.reservasActivas() > 0
                ? propiedades.esperaConReservaSegundos()
                : propiedades.esperaParadaSegundos();
    }

    /**
     * Velocidad de cada lectura: la que reporta el equipo, o si no la trae, la
     * deducida de la distancia y el tiempo hasta la lectura anterior. Null si no
     * hay como saberla.
     *
     * @param lecturas de la mas nueva a la mas vieja
     */
    static List<Double> velocidadesEfectivas(List<Lectura> lecturas) {
        List<Double> velocidades = new ArrayList<>(lecturas.size());
        for (int i = 0; i < lecturas.size(); i++) {
            Lectura lectura = lecturas.get(i);
            if (lectura.kmh() != null) {
                velocidades.add(lectura.kmh());
                continue;
            }
            Double deducida = null;
            if (i + 1 < lecturas.size()) {
                Lectura anterior = lecturas.get(i + 1);
                double segundos = Duration.between(anterior.en(), lectura.en()).toMillis() / 1000d;
                if (segundos > 0) {
                    double kmh = metrosEntre(anterior, lectura) / segundos * 3.6;
                    deducida = kmh <= VELOCIDAD_MAXIMA_CREIBLE_KMH ? kmh : null;
                }
            }
            velocidades.add(deducida);
        }
        return velocidades;
    }

    /**
     * Cuanto lleva el bus detenido, contado desde la lectura lenta mas antigua
     * de la racha actual. Cero si la ultima lectura no es lenta.
     */
    static Duration tiempoDetenido(List<Lectura> lecturas, List<Double> velocidades, double minimaKmh) {
        Instant desde = null;
        for (int i = 0; i < lecturas.size(); i++) {
            Double kmh = velocidades.get(i);
            if (kmh == null || kmh >= minimaKmh) {
                break;
            }
            desde = lecturas.get(i).en();
        }
        return desde == null ? Duration.ZERO : Duration.between(desde, lecturas.getFirst().en());
    }

    static Optional<ParadaEnTrazado> paradaCercana(List<ParadaEnTrazado> paradas, Lectura bus, int radioMetros) {
        return paradas.stream()
                .filter(p -> metrosEntre(bus, new Lectura(p.latitud(), p.longitud(), null, bus.en())) <= radioMetros)
                .findFirst();
    }

    /**
     * Haversine. Para distancias de pocos cientos de metros difiere de
     * ST_Distance sobre geography en menos de 0.5 %, y evita una consulta por par.
     */
    static double metrosEntre(Lectura a, Lectura b) {
        double radioTierra = 6_371_008.8;
        double dLat = Math.toRadians(b.latitud() - a.latitud());
        double dLon = Math.toRadians(b.longitud() - a.longitud());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitud())) * Math.cos(Math.toRadians(b.latitud()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * radioTierra * Math.asin(Math.sqrt(h));
    }

    // -----------------------------------------------------------------------
    // Acceso a datos
    // -----------------------------------------------------------------------

    private OptionalDouble velocidadObservada(List<Lectura> lecturas, List<Double> velocidades, Instant ultima) {
        Instant desde = ultima.minus(propiedades.ventanaVelocidad());
        List<Double> muestras = new ArrayList<>();
        for (int i = 0; i < lecturas.size() && muestras.size() < propiedades.muestrasVelocidad(); i++) {
            if (lecturas.get(i).en().isBefore(desde)) {
                break;
            }
            Double kmh = velocidades.get(i);
            if (kmh != null && kmh >= propiedades.velocidadMinimaKmh()) {
                muestras.add(kmh);
            }
        }
        return muestras.stream().mapToDouble(Double::doubleValue).average();
    }

    private List<Lectura> lecturasRecientes(Long vehiculoId, Instant ultima) {
        return posiciones
                .findByVehiculoIdAndRegistradoEnGreaterThanEqualOrderByRegistradoEnDescIdDesc(
                        vehiculoId, ultima.minus(HISTORIAL), PageRequest.of(0, MAXIMO_LECTURAS))
                .stream()
                .map(p -> new Lectura(Geo.latitud(p.getUbicacion()), Geo.longitud(p.getUbicacion()),
                        p.getVelocidadKmh(), p.getRegistradoEn()))
                .toList();
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
                               COALESCE(ST_LineLocatePoint(r.trazado, p.ubicacion), 0) AS fraccion,
                               ST_Y(p.ubicacion) AS latitud, ST_X(p.ubicacion) AS longitud,
                               (SELECT count(*) FROM registros_espera e
                                 WHERE e.parada_id = p.id
                                   AND e.estado IN ('ACTIVA', 'RENOVADA')
                                   AND e.expira_en > now()) AS reservas
                          FROM paradas p
                          JOIN rutas r ON r.id = p.ruta_id
                         WHERE r.id = ?
                           AND p.retirada_en IS NULL
                         ORDER BY p.orden
                        """,
                (rs, i) -> new ParadaEnTrazado(rs.getLong("id"), rs.getInt("orden"), rs.getDouble("fraccion"),
                        rs.getDouble("latitud"), rs.getDouble("longitud"), rs.getInt("reservas")),
                rutaId);
    }

    /** Donde cae el bus sobre el trazado. Vacio si la ruta no tiene trazado. */
    private Optional<Trazado> ubicarBus(Long rutaId, PosicionHistorica posicion) {
        // Metros con geography, como pide el ADR-007. El punto va en (lon, lat).
        return jdbc.query("""
                        SELECT ST_LineLocatePoint(r.trazado, b.p) AS fraccion,
                               ST_Length(r.trazado::geography) AS largo,
                               ST_IsClosed(r.trazado) AS circuito,
                               ST_Distance(r.trazado::geography, b.p::geography) AS fuera
                          FROM rutas r
                         CROSS JOIN (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS p) b
                         WHERE r.id = ? AND r.trazado IS NOT NULL
                        """,
                (rs, i) -> new Trazado(rs.getDouble("fraccion"), rs.getDouble("largo"),
                        rs.getBoolean("circuito"), rs.getDouble("fuera")),
                Geo.longitud(posicion.getUbicacion()), Geo.latitud(posicion.getUbicacion()), rutaId)
                .stream().findFirst();
    }

    /**
     * Arma el camino que se estima seguira el bus fuera del trazado: lo que ya
     * recorrio desde que salio, el punto de reincorporacion hacia adelante mas
     * cercano, y el resto del trazado original.
     */
    private DesvioCalculado calcularDesvio(Long rutaId, Long vehiculoId, PosicionHistorica posicion,
                                           Trazado trazado, List<Lectura> lecturas) {
        // La ultima lectura todavia sobre el trazado marca por donde salio.
        Optional<Salida> salida = jdbc.query("""
                        SELECT ST_LineLocatePoint(r.trazado, ph.ubicacion) AS fraccion, ph.registrado_en
                          FROM posiciones_historicas ph
                          JOIN rutas r ON r.id = ?
                         WHERE ph.vehiculo_id = ?
                           AND ph.registrado_en >= ?
                           AND ST_Distance(ph.ubicacion::geography, r.trazado::geography) <= ?
                         ORDER BY ph.registrado_en DESC, ph.id DESC
                         LIMIT 1
                        """,
                (rs, i) -> new Salida(rs.getDouble("fraccion"), rs.getTimestamp("registrado_en").toInstant()),
                rutaId, vehiculoId, Timestamp.from(posicion.getRegistradoEn().minus(HISTORIAL)),
                propiedades.desvioMetros()).stream().findFirst();
        double fraccionSalida = Math.min(salida.map(Salida::fraccion).orElse(trazado.fraccionBus()), 0.999999);

        Punto volver = jdbc.queryForObject("""
                        SELECT ST_LineLocatePoint(r.trazado, c.punto) AS fraccion,
                               ST_Distance(c.punto::geography, b.p::geography) AS metros,
                               ST_Y(c.punto) AS latitud, ST_X(c.punto) AS longitud
                          FROM rutas r
                         CROSS JOIN (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS p) b
                         CROSS JOIN LATERAL (
                               SELECT ST_ClosestPoint(ST_LineSubstring(r.trazado, ?, 1), b.p) AS punto) c
                         WHERE r.id = ?
                        """,
                (rs, i) -> new Punto(rs.getDouble("fraccion"), rs.getDouble("metros"),
                        rs.getDouble("latitud"), rs.getDouble("longitud")),
                Geo.longitud(posicion.getUbicacion()), Geo.latitud(posicion.getUbicacion()),
                fraccionSalida, rutaId);

        /*
         * SCRUM-26, bloque C. La vuelta al trazado se mide por las calles de
         * Jalapa (pgRouting sobre la red importada de OSM). Entre los puntos
         * del trazado que quedan por delante gana el que da el camino por
         * calles mas corto, que no siempre es el mas cercano en linea recta.
         * Sin red, sin calle cerca o sin camino posible se vuelve al factor.
         */
        PuntoResponse posicionDelBus = new PuntoResponse(
                Geo.latitud(posicion.getUbicacion()), Geo.longitud(posicion.getUbicacion()));
        List<Punto> candidatos = candidatosDeReincorporacion(rutaId, fraccionSalida, volver);
        Optional<RedDeCalles.Camino> porCalles = calles.caminoMasCorto(posicionDelBus,
                candidatos.stream().map(c -> new PuntoResponse(c.latitud(), c.longitud())).toList());

        Punto elegido = porCalles.map(camino -> candidatos.get(camino.destino())).orElse(volver);
        double metrosHastaVolver = porCalles.map(RedDeCalles.Camino::metros)
                .orElseGet(() -> volver.metros() * propiedades.factorDesvio());

        Reincorporacion reincorporacion = new Reincorporacion(fraccionSalida, elegido.fraccion(),
                metrosHastaVolver);

        List<PuntoResponse> recorrido = new ArrayList<>();
        Instant salioEn = salida.map(Salida::en).orElse(posicion.getRegistradoEn());
        lecturas.reversed().stream()
                .filter(l -> !l.en().isBefore(salioEn))
                .forEach(l -> recorrido.add(new PuntoResponse(l.latitud(), l.longitud())));
        PuntoResponse puntoDeVuelta = new PuntoResponse(elegido.latitud(), elegido.longitud());
        porCalles.ifPresent(camino -> recorrido.addAll(camino.trazo()));
        recorrido.add(puntoDeVuelta);
        if (elegido.fraccion() < 1) {
            recorrido.addAll(jdbc.query("""
                            SELECT ST_Y(g.geom) AS latitud, ST_X(g.geom) AS longitud
                              FROM rutas r, ST_DumpPoints(ST_LineSubstring(r.trazado, ?, 1)) g
                             WHERE r.id = ?
                             ORDER BY g.path
                            """,
                    (rs, i) -> new PuntoResponse(rs.getDouble("latitud"), rs.getDouble("longitud")),
                    elegido.fraccion(), rutaId));
        }

        DesvioResponse respuesta = new DesvioResponse(
                (int) Math.round(trazado.metrosAlTrazado()),
                (int) Math.round(reincorporacion.metrosHastaReincorporar()),
                puntoDeVuelta, recorrido);
        return new DesvioCalculado(reincorporacion, respuesta);
    }

    /**
     * Puntos del trazado que quedan por delante y se prueban como
     * reincorporacion. Se toma una muestra repartida a lo largo del tramo, mas
     * el punto mas cercano al bus, para no pedirle a pgRouting cientos de
     * destinos en cada posicion.
     */
    private List<Punto> candidatosDeReincorporacion(Long rutaId, double fraccionSalida, Punto cercano) {
        List<Punto> delTrazado = jdbc.query("""
                        SELECT ST_LineLocatePoint(r.trazado, g.geom) AS fraccion,
                               ST_Y(g.geom) AS latitud, ST_X(g.geom) AS longitud
                          FROM rutas r, ST_DumpPoints(ST_LineSubstring(r.trazado, ?, 1)) g
                         WHERE r.id = ?
                         ORDER BY g.path
                        """,
                (rs, i) -> new Punto(rs.getDouble("fraccion"), 0,
                        rs.getDouble("latitud"), rs.getDouble("longitud")),
                fraccionSalida, rutaId);

        int maximo = Math.max(1, callesPropiedades.candidatos());
        List<Punto> muestra = new ArrayList<>();
        muestra.add(cercano);
        if (!delTrazado.isEmpty()) {
            int paso = Math.max(1, delTrazado.size() / maximo);
            for (int i = 0; i < delTrazado.size() && muestra.size() <= maximo; i += paso) {
                muestra.add(delTrazado.get(i));
            }
        }
        return muestra;
    }

    private static EtaRutaResponse sinEstimacion(Long rutaId, Long vehiculoId, Instant ahora,
                                                 EstadoDelBus estado, List<ParadaEnTrazado> paradas) {
        return new EtaRutaResponse(rutaId, vehiculoId, ahora, estado, null, paradas.stream()
                .map(p -> EtaParadaResponse.noDisponible(p.paradaId(), p.orden()))
                .toList());
    }

    record ParadaEnTrazado(Long paradaId, int orden, double fraccion,
                           double latitud, double longitud, int reservasActivas) {

        ParadaEnTrazado(Long paradaId, int orden, double fraccion) {
            this(paradaId, orden, fraccion, 0, 0, 0);
        }
    }

    /** @param metrosAlTrazado distancia del bus a la linea; grande = desvio */
    record Trazado(double fraccionBus, double largoMetros, boolean circuito, double metrosAlTrazado) {

        Trazado(double fraccionBus, double largoMetros, boolean circuito) {
            this(fraccionBus, largoMetros, circuito, 0);
        }
    }

    record Lectura(double latitud, double longitud, Double kmh, Instant en) {
    }

    /**
     * @param fraccionSalida          donde dejo el trazado
     * @param fraccionReincorporacion donde se estima que vuelve
     */
    record Reincorporacion(double fraccionSalida, double fraccionReincorporacion,
                           double metrosHastaReincorporar) {
    }

    private record Salida(double fraccion, Instant en) {
    }

    private record Punto(double fraccion, double metros, double latitud, double longitud) {
    }

    private record DesvioCalculado(Reincorporacion reincorporacion, DesvioResponse respuesta) {
    }

    /**
     * @param posicionEn instante de la posicion usada, para revalidar su
     *                   antiguedad al servir el resultado desde la cache
     */
    public record EtaCalculado(EtaRutaResponse respuesta, Instant posicionEn) {
    }
}
