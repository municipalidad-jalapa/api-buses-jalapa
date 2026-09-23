package gt.muni.jalapa.ecoruta.opiniones.servicio;

import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.opiniones.OpinionesProperties;
import gt.muni.jalapa.ecoruta.opiniones.dominio.Opinion;
import gt.muni.jalapa.ecoruta.opiniones.dominio.TipoOpinion;
import gt.muni.jalapa.ecoruta.opiniones.repositorio.OpinionRepository;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.AtendidaResponse;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.CrearOpinionRequest;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.OpinionCreadaResponse;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.OpinionResponse;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.PaginaDeOpiniones;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.Promedio;
import gt.muni.jalapa.ecoruta.opiniones.web.dto.OpinionesDtos.Resumen;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro y atencion de las opiniones del servicio (SCRUM-26, bloque A).
 */
@Service
@RequiredArgsConstructor
public class OpinionService {

    public static final int TAMANO_MAXIMO_PAGINA = 100;

    private final OpinionRepository opiniones;
    private final RutaRepository rutas;
    private final VehiculoRepository vehiculos;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final OpinionesProperties propiedades;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    /**
     * Registra una opinion, atribuida al identificador del navegador y, si hay
     * sesion de pasajero, tambien a su cuenta. El vehiculo lo resuelve el
     * servidor: el que tiene asignada la ruta ahora.
     *
     * @param pasajeroId cuenta del pasajero; null si opina como invitado
     */
    @Transactional
    public OpinionCreadaResponse registrar(String dispositivoId, Long pasajeroId, CrearOpinionRequest peticion) {
        if (!StringUtils.hasText(dispositivoId) || dispositivoId.length() > 64) {
            throw new ReglaDeNegocioException("Falta el identificador del dispositivo (X-Dispositivo-Id).");
        }
        if (peticion == null || peticion.tipo() == null) {
            throw new ReglaDeNegocioException("El tipo debe ser queja, comentario o calificacion.");
        }
        String texto = peticion.texto(); // Validar contenido no debe modificar el valor que se almacena.
        Integer estrellas = peticion.estrellas();
        // SCRUM-26, bloque F: las tres valoraciones cuentan como contenido.
        boolean hayValoracion = estrellas != null || peticion.calidad() != null
                || peticion.limpieza() != null || peticion.conduccion() != null;
        if (!StringUtils.hasText(texto) && !hayValoracion) {
            throw new ReglaDeNegocioException("Escribe un comentario o elige una calificacion.");
        }
        validarEstrellas(estrellas, "La calificacion");
        validarEstrellas(peticion.calidad(), "La calidad del servicio");
        validarEstrellas(peticion.limpieza(), "La limpieza de la unidad");
        validarEstrellas(peticion.conduccion(), "La conduccion del piloto");
        if (texto != null && texto.codePointCount(0, texto.length()) > propiedades.textoMaximo()) {
            throw new ReglaDeNegocioException(
                    "El comentario admite hasta %d caracteres.".formatted(propiedades.textoMaximo()));
        }
        if (peticion.rutaId() == null || !rutas.existsById(peticion.rutaId())) {
            throw new ReglaDeNegocioException("La ruta no existe.");
        }
        if (peticion.reservaId() != null
                && reservas.findByIdAndDispositivoId(peticion.reservaId(), dispositivoId).isEmpty()) {
            throw new ReglaDeNegocioException("La reserva no existe o no es de este dispositivo.");
        }

        Instant desde = reloj.instant().minus(propiedades.ventana());
        if (opiniones.countByDispositivoIdAndCreadaEnAfter(dispositivoId, desde) >= propiedades.limiteEnvios()) {
            throw new LimiteDeOpinionesExcedido();
        }

        Opinion opinion = new Opinion();
        opinion.setTipo(peticion.tipo());
        opinion.setRutaId(peticion.rutaId());
        opinion.setVehiculoId(vehiculos.findFirstByRutaIdAndActivoTrue(peticion.rutaId())
                .map(Vehiculo::getId).orElse(null));
        opinion.setReservaId(peticion.reservaId());
        opinion.setDispositivoId(dispositivoId);
        opinion.setPasajeroId(pasajeroId);
        opinion.setTexto(texto);
        opinion.setEstrellas(estrellas);
        opinion.setCalidad(peticion.calidad());
        opinion.setLimpieza(peticion.limpieza());
        opinion.setConduccion(peticion.conduccion());
        Opinion guardada = opiniones.save(opinion);
        return new OpinionCreadaResponse(guardada.getId(), guardada.getRutaId(), guardada.getVehiculoId());
    }

    private static void validarEstrellas(Integer valor, String que) {
        if (valor != null && (valor < 1 || valor > 5)) {
            throw new ReglaDeNegocioException("%s va de 1 a 5 estrellas.".formatted(que));
        }
    }

    /** Filtros opcionales: null = sin filtrar por ese campo. */
    public record Filtros(TipoOpinion tipo, Long rutaId, Long vehiculoId, Instant desde, Instant hasta,
                          int pagina, int tamano) {
    }

    @Transactional(readOnly = true)
    public PaginaDeOpiniones listar(Filtros filtros) {
        int pagina = Math.max(0, filtros.pagina());
        int tamano = Math.clamp(filtros.tamano(), 1, TAMANO_MAXIMO_PAGINA);

        List<Object> parametros = new ArrayList<>();
        String donde = condiciones(filtros, parametros);

        long total = jdbc.queryForObject("SELECT count(*) FROM opiniones o" + donde, Long.class,
                parametros.toArray());

        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(tamano);
        conPagina.add((long) pagina * tamano);
        List<OpinionResponse> lista = jdbc.query("""
                        SELECT o.id, o.creada_en, o.tipo, o.ruta_id, r.nombre AS ruta, o.vehiculo_id,
                               v.identificador AS vehiculo, o.estrellas,
                               o.calidad, o.limpieza, o.conduccion,
                               o.texto, o.atendida_en, o.atendida_por
                          FROM opiniones o
                          JOIN rutas r ON r.id = o.ruta_id
                          LEFT JOIN vehiculos v ON v.id = o.vehiculo_id
                        """ + donde + " ORDER BY o.creada_en DESC, o.id DESC LIMIT ? OFFSET ?",
                FILA, conPagina.toArray());

        Resumen resumen = new Resumen(total,
                promedios("o.ruta_id", "r.nombre", "JOIN rutas r ON r.id = o.ruta_id", donde, parametros),
                promedios("o.vehiculo_id", "v.identificador", "JOIN vehiculos v ON v.id = o.vehiculo_id",
                        donde, parametros));
        return new PaginaDeOpiniones(total, pagina, tamano, lista, resumen);
    }

    /**
     * Marca la opinion como atendida. Si ya lo estaba, conserva quien y cuando
     * la atendio primero: no se reescribe el registro.
     */
    @Transactional
    public AtendidaResponse marcarAtendida(Long id, String principal) {
        Opinion opinion = opiniones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Opinion", id));
        if (!opinion.estaAtendida()) {
            opinion.setAtendidaEn(reloj.instant());
            opinion.setAtendidaPor(quien(principal));
        }
        return new AtendidaResponse(opinion.getId(), opinion.getAtendidaPor(), opinion.getAtendidaEn());
    }

    /** El JWT del panel lleva el uid de Firebase; se registra el nombre de la cuenta. */
    private String quien(String principal) {
        return usuarios.findByFirebaseUid(principal).map(Usuario::getUsername).orElse(principal);
    }

    private static String condiciones(Filtros filtros, List<Object> parametros) {
        StringBuilder donde = new StringBuilder(" WHERE 1 = 1");
        if (filtros.tipo() != null) {
            donde.append(" AND o.tipo = ?");
            parametros.add(filtros.tipo().name());
        }
        if (filtros.rutaId() != null) {
            donde.append(" AND o.ruta_id = ?");
            parametros.add(filtros.rutaId());
        }
        if (filtros.vehiculoId() != null) {
            donde.append(" AND o.vehiculo_id = ?");
            parametros.add(filtros.vehiculoId());
        }
        if (filtros.desde() != null) {
            donde.append(" AND o.creada_en >= ?");
            parametros.add(Timestamp.from(filtros.desde()));
        }
        if (filtros.hasta() != null) {
            donde.append(" AND o.creada_en < ?");
            parametros.add(Timestamp.from(filtros.hasta()));
        }
        return donde.toString();
    }

    /**
     * Promedios agrupados, con cada dimension por separado (bloque F). Una
     * dimension que nadie puntuo sale en null: es "sin datos", no un cero.
     */
    private List<Promedio> promedios(String id, String nombre, String union, String donde, List<Object> parametros) {
        return jdbc.query("SELECT " + id + " AS id, " + nombre + " AS nombre, "
                        + "avg(o.estrellas) AS promedio, avg(o.calidad) AS calidad, "
                        + "avg(o.limpieza) AS limpieza, avg(o.conduccion) AS conduccion, "
                        + "count(o.estrellas) AS calificadas, count(*) AS opiniones "
                        + "FROM opiniones o " + union + donde
                        + " GROUP BY " + id + ", " + nombre + " ORDER BY " + nombre,
                (rs, i) -> new Promedio(rs.getLong("id"), rs.getString("nombre"),
                        redondear(rs, "promedio"), redondear(rs, "calidad"),
                        redondear(rs, "limpieza"), redondear(rs, "conduccion"),
                        rs.getLong("calificadas"), rs.getLong("opiniones")),
                parametros.toArray());
    }

    /** Un decimal, o null si nadie puntuo. */
    private static Double redondear(java.sql.ResultSet rs, String columna) throws java.sql.SQLException {
        double valor = rs.getDouble(columna);
        return rs.wasNull() ? null : Math.round(valor * 10) / 10.0;
    }

    private static Integer entero(java.sql.ResultSet rs, String columna) throws java.sql.SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    /** El texto sale neutralizado: lo que la persona escribio se muestra, nunca se interpreta. */
    private static final RowMapper<OpinionResponse> FILA = (rs, i) -> {
        Timestamp atendida = rs.getTimestamp("atendida_en");
        String texto = rs.getString("texto");
        int estrellas = rs.getInt("estrellas");
        Integer conEstrellas = rs.wasNull() ? null : estrellas;
        long vehiculoId = rs.getLong("vehiculo_id");
        Long conVehiculo = rs.wasNull() ? null : vehiculoId;
        return new OpinionResponse(
                rs.getLong("id"),
                rs.getTimestamp("creada_en").toInstant(),
                TipoOpinion.valueOf(rs.getString("tipo")),
                rs.getLong("ruta_id"),
                rs.getString("ruta"),
                conVehiculo,
                rs.getString("vehiculo"),
                conEstrellas,
                entero(rs, "calidad"),
                entero(rs, "limpieza"),
                entero(rs, "conduccion"),
                texto == null ? null : HtmlUtils.htmlEscape(texto),
                atendida == null ? null : atendida.toInstant(),
                rs.getString("atendida_por") == null ? null : HtmlUtils.htmlEscape(rs.getString("atendida_por")));
    };
}
