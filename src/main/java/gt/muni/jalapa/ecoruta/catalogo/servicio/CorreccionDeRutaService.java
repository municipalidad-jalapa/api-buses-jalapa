package gt.muni.jalapa.ecoruta.catalogo.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirParadaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirTrazadoRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Correccion de rutas desde el panel municipal (QA 5.6).
 *
 * <p>Corrige lo que se levanto mal en campo: el recorrido sobre las calles y
 * el nombre o la ubicacion de una parada. No crea ni borra paradas: eso
 * cambiaria el conteo de reservas y el orden del recorrido, y queda para el
 * modelo de SuperAdmin (SCRUM-26, bloque D).
 *
 * <p>El trazado nuevo lo usa el ETA en su siguiente calculo: se lee de la base
 * cada vez.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreccionDeRutaService {

    private final RutaRepository rutas;
    private final ParadaRepository paradas;
    private final OrdenDeParadas orden;

    /** Todas las rutas no eliminadas, activas o no, con sus paradas y su trazado. */
    @Transactional(readOnly = true)
    public List<RutaResponse> listarTodas() {
        return rutas.findByEliminadaEnIsNullOrderByIdAsc().stream()
                .map(RutaResponse::de)
                .toList();
    }

    /**
     * Reemplaza el trazado. El editor lo manda solo con cada cambio (guardado
     * automatico), asi que sin puntos deja la ruta sin recorrido: es "empezar
     * de nuevo".
     *
     * <p>En un borrador las paradas se renumeran segun el recorrido nuevo. Una
     * ruta publicada conserva su orden: corregir el trazado de una ruta de ida
     * y vuelta no puede desordenar paradas que ya usan los pasajeros.
     */
    @Transactional
    public RutaResponse corregirTrazado(Long rutaId, CorregirTrazadoRequest peticion, String quien) {
        Ruta ruta = rutas.findById(rutaId)
                .filter(r -> !r.estaEliminada())
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId));
        int puntos = peticion.puntos().size();
        if (puntos == 1) {
            throw new ReglaDeNegocioException("El recorrido necesita al menos 2 puntos.");
        }
        if (puntos == 0 && ruta.isActiva()) {
            throw new ReglaDeNegocioException(
                    "Una ruta publicada necesita su recorrido. Ocultala primero para empezarlo de nuevo.");
        }
        ruta.setTrazado(puntos == 0 ? null : Geo.linea(peticion.puntos().stream()
                .map(p -> new double[] {p.latitud(), p.longitud()})
                .toList()));
        log.info("Trazado de la ruta {} corregido por {}: {} puntos", rutaId, quien, puntos);
        if (!ruta.isActiva()) {
            orden.segunElRecorrido(rutaId);
        }
        return releer(rutaId);
    }

    @Transactional
    public RutaResponse corregirParada(Long rutaId, Long paradaId, CorregirParadaRequest peticion, String quien) {
        Parada parada = paradas.findById(paradaId)
                .filter(p -> p.getRuta().getId().equals(rutaId))
                .filter(p -> !p.estaRetirada() && !p.getRuta().estaEliminada())
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", paradaId));
        Point nueva = Geo.punto(peticion.latitud(), peticion.longitud());
        // Cambiarle solo el nombre no la mueve de lugar en el recorrido.
        boolean seMovio = !parada.getUbicacion().equalsExact(nueva, 1e-7);
        parada.setNombre(peticion.nombre().trim());
        parada.setUbicacion(nueva);
        log.info("Parada {} de la ruta {} corregida por {}", paradaId, rutaId, quien);
        if (seMovio) {
            orden.ubicar(rutaId, paradaId);
        }
        return releer(rutaId);
    }

    private RutaResponse releer(Long rutaId) {
        return rutas.buscarConParadas(rutaId).map(RutaResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId));
    }
}
