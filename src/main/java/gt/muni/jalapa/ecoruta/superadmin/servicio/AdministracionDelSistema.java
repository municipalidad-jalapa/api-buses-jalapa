package gt.muni.jalapa.ecoruta.superadmin.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.identidad.dominio.Rol;
import gt.muni.jalapa.ecoruta.identidad.dominio.Usuario;
import gt.muni.jalapa.ecoruta.identidad.repositorio.UsuarioRepository;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.CrearCuentaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.CuentaResponse;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.EditarCuentaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.GuardarParadaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.GuardarRutaRequest;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.ParadaResponse;
import gt.muni.jalapa.ecoruta.superadmin.web.dto.SuperAdminDtos.RutaResponse;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Administracion del sistema, reservada al SuperAdmin (SCRUM-26, bloque D).
 *
 * <p>Cuentas de los otros roles, rutas y paradas. Los vehiculos y sus equipos a
 * bordo ya tienen su propio modulo ({@code flota}); lo que cambia con este
 * bloque es quien puede invocarlo, no como funciona.
 *
 * <p>Regla que se protege aqui y no en la pantalla: nunca puede quedar el
 * sistema sin ningun SuperAdmin activo, porque nadie podria volver a crear uno.
 */
@Service
@RequiredArgsConstructor
public class AdministracionDelSistema {

    private final UsuarioRepository usuarios;
    private final JdbcTemplate jdbc;
    private final RutaRepository rutas;
    private final ParadaRepository paradas;

    // --- cuentas -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CuentaResponse> cuentas() {
        return usuarios.findAllByOrderByUsernameAsc().stream().map(CuentaResponse::de).toList();
    }

    @Transactional
    public CuentaResponse crearCuenta(CrearCuentaRequest peticion) {
        if (peticion == null || !StringUtils.hasText(peticion.username()) || peticion.rol() == null) {
            throw new ReglaDeNegocioException("La cuenta necesita un usuario y un rol.");
        }
        String username = peticion.username().strip();
        if (usuarios.existsByUsername(username)) {
            throw new ReglaDeNegocioException("Ya existe una cuenta con ese usuario.");
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRol(peticion.rol());
        usuario.setActivo(true);
        usuario.setFirebaseUid(uidValido(peticion.firebaseUid()));
        usuario.setRutaId(rutaDelRol(peticion.rol(), peticion.rutaId()));
        return CuentaResponse.de(usuarios.save(usuario));
    }

    @Transactional
    public CuentaResponse editarCuenta(Long id, EditarCuentaRequest peticion) {
        // Un único bloqueo transaccional de PostgreSQL serializa la decisión entre JVMs.
        // Adquirir ANTES de leer usuarios evita entidades obsoletas en el contexto JPA.
        // Se libera al commit/rollback, después del flush de la modificación.
        jdbc.execute("SELECT pg_advisory_xact_lock(146, 26)");
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id));
        if (peticion == null) {
            return CuentaResponse.de(usuario);
        }
        if (peticion.rol() != null) {
            if (dejariaSinSuperAdmin(usuario, peticion.rol(), usuario.isActivo())) {
                throw new ReglaDeNegocioException(
                        "No se puede quitar el ultimo SuperAdmin activo del sistema.");
            }
            usuario.setRol(peticion.rol());
            usuario.setRutaId(rutaDelRol(peticion.rol(), peticion.rutaId() != null
                    ? peticion.rutaId() : usuario.getRutaId()));
        } else if (peticion.rutaId() != null) {
            usuario.setRutaId(rutaDelRol(usuario.getRol(), peticion.rutaId()));
        }
        if (peticion.firebaseUid() != null) {
            usuario.setFirebaseUid(uidValido(peticion.firebaseUid()));
        }
        if (peticion.activo() != null) {
            if (dejariaSinSuperAdmin(usuario, usuario.getRol(), peticion.activo())) {
                throw new ReglaDeNegocioException(
                        "No se puede desactivar el ultimo SuperAdmin activo del sistema.");
            }
            usuario.setActivo(peticion.activo());
        }
        return CuentaResponse.de(usuario);
    }

    /** Desactivar no borra: la cuenta queda, deja de entrar y conserva su historial. */
    @Transactional
    public CuentaResponse desactivarCuenta(Long id) {
        return editarCuenta(id, new EditarCuentaRequest(null, null, null, false));
    }

    private boolean dejariaSinSuperAdmin(Usuario usuario, Rol rolNuevo, boolean activo) {
        boolean seguiriaSiendoSuperAdminActivo = rolNuevo.administraElSistema() && activo;
        if (!usuario.puedeAdministrarElSistema() || seguiriaSiendoSuperAdminActivo) {
            return false;
        }
        long activos = usuarios.findAllByOrderByUsernameAsc().stream()
                .filter(Usuario::puedeAdministrarElSistema)
                .count();
        return activos <= 1;
    }

    /** Solo el piloto esta atado a una ruta; los demas roles no llevan ninguna. */
    private Long rutaDelRol(Rol rol, Long rutaId) {
        if (!rol.esConductor()) {
            return null;
        }
        if (rutaId != null && !rutas.existsById(rutaId)) {
            throw new ReglaDeNegocioException("La ruta no existe.");
        }
        return rutaId;
    }

    private static String uidValido(String uid) {
        return StringUtils.hasText(uid) ? uid.strip() : null;
    }

    // --- rutas y paradas -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<RutaResponse> rutas() {
        return rutas.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(ruta -> new RutaResponse(ruta.getId(), ruta.getNombre(), ruta.isActiva(),
                        ruta.getParadas().size()))
                .toList();
    }

    @Transactional
    public RutaResponse crearRuta(GuardarRutaRequest peticion) {
        if (peticion == null || !StringUtils.hasText(peticion.nombre())) {
            throw new ReglaDeNegocioException("La ruta necesita un nombre.");
        }
        Ruta ruta = new Ruta();
        ruta.setNombre(peticion.nombre().strip());
        ruta.setActiva(peticion.activa() == null || peticion.activa());
        ruta.setTrazado(trazado(peticion.trazado()));
        Ruta guardada = rutas.save(ruta);
        return new RutaResponse(guardada.getId(), guardada.getNombre(), guardada.isActiva(), 0);
    }

    @Transactional
    public RutaResponse editarRuta(Long id, GuardarRutaRequest peticion) {
        Ruta ruta = rutas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", id));
        if (peticion != null) {
            if (StringUtils.hasText(peticion.nombre())) {
                ruta.setNombre(peticion.nombre().strip());
            }
            if (peticion.activa() != null) {
                ruta.setActiva(peticion.activa());
            }
            if (StringUtils.hasText(peticion.trazado())) {
                ruta.setTrazado(trazado(peticion.trazado()));
            }
        }
        return new RutaResponse(ruta.getId(), ruta.getNombre(), ruta.isActiva(), ruta.getParadas().size());
    }

    @Transactional
    public ParadaResponse crearParada(GuardarParadaRequest peticion) {
        if (peticion == null || !StringUtils.hasText(peticion.nombre())
                || peticion.latitud() == null || peticion.longitud() == null
                || peticion.orden() == null || peticion.rutaId() == null) {
            throw new ReglaDeNegocioException(
                    "La parada necesita nombre, ubicacion, orden y ruta.");
        }
        Ruta ruta = rutas.findById(peticion.rutaId())
                .orElseThrow(() -> new ReglaDeNegocioException("La ruta no existe."));
        Parada parada = new Parada();
        parada.setNombre(peticion.nombre().strip());
        parada.setUbicacion(Geo.punto(peticion.latitud(), peticion.longitud()));
        parada.setOrden(peticion.orden());
        parada.setRuta(ruta);
        return respuesta(paradas.save(parada));
    }

    @Transactional
    public ParadaResponse editarParada(Long id, GuardarParadaRequest peticion) {
        Parada parada = paradas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", id));
        if (peticion != null) {
            if (StringUtils.hasText(peticion.nombre())) {
                parada.setNombre(peticion.nombre().strip());
            }
            if (peticion.latitud() != null && peticion.longitud() != null) {
                parada.setUbicacion(Geo.punto(peticion.latitud(), peticion.longitud()));
            }
            if (peticion.orden() != null) {
                parada.setOrden(peticion.orden());
            }
            if (peticion.rutaId() != null) {
                parada.setRuta(rutas.findById(peticion.rutaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La ruta no existe.")));
            }
        }
        return respuesta(parada);
    }

    private static ParadaResponse respuesta(Parada parada) {
        return new ParadaResponse(parada.getId(), parada.getNombre(),
                Geo.latitud(parada.getUbicacion()), Geo.longitud(parada.getUbicacion()),
                parada.getOrden(), parada.getRuta().getId());
    }

    /** El trazado llega como WKT; un texto que no sea una linea es 422, no un 500. */
    private static LineString trazado(String wkt) {
        if (!StringUtils.hasText(wkt)) {
            return null;
        }
        try {
            org.locationtech.jts.geom.Geometry geometria = new WKTReader().read(wkt.strip());
            if (!(geometria instanceof LineString linea)) {
                throw new ReglaDeNegocioException("El trazado debe ser un LINESTRING.");
            }
            linea.setSRID(4326);
            return linea;
        } catch (ParseException ex) {
            throw new ReglaDeNegocioException("El trazado no es un WKT valido.");
        }
    }
}
