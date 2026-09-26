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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

    /** Todas las rutas, activas o no, con sus paradas y su trazado. */
    @Transactional(readOnly = true)
    public List<RutaResponse> listarTodas() {
        return rutas.findAll().stream()
                .sorted(Comparator.comparing(Ruta::getId))
                .map(RutaResponse::de)
                .toList();
    }

    @Transactional
    public RutaResponse corregirTrazado(Long rutaId, CorregirTrazadoRequest peticion, String quien) {
        Ruta ruta = rutas.findById(rutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId));
        ruta.setTrazado(Geo.linea(peticion.puntos().stream()
                .map(p -> new double[] {p.latitud(), p.longitud()})
                .toList()));
        log.info("Trazado de la ruta {} corregido por {}: {} puntos", rutaId, quien, peticion.puntos().size());
        return RutaResponse.de(ruta);
    }

    @Transactional
    public RutaResponse corregirParada(Long rutaId, Long paradaId, CorregirParadaRequest peticion, String quien) {
        Parada parada = paradas.findById(paradaId)
                .filter(p -> p.getRuta().getId().equals(rutaId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", paradaId));
        parada.setNombre(peticion.nombre().trim());
        parada.setUbicacion(Geo.punto(peticion.latitud(), peticion.longitud()));
        log.info("Parada {} de la ruta {} corregida por {}", paradaId, rutaId, quien);
        return RutaResponse.de(parada.getRuta());
    }
}
