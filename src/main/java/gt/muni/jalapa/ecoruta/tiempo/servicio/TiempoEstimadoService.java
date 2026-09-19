package gt.muni.jalapa.ecoruta.tiempo.servicio;

import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.tiempo.dominio.ParametrosDeRuta;
import gt.muni.jalapa.ecoruta.tiempo.repositorio.ParametrosDeRutaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

/** Lee los parametros de la base y calcula el minuto estimado (HU-72). */
@Service
public class TiempoEstimadoService {

    private final ParametrosDeRutaRepository parametros;
    private final RutaRepository rutas;
    private final CalculadorDeTiempoEstimado calculador = new CalculadorDeTiempoEstimado();
    private final Clock reloj = Clock.system(CalculadorDeTiempoEstimado.ZONA);

    public TiempoEstimadoService(ParametrosDeRutaRepository parametros, RutaRepository rutas) {
        this.parametros = parametros;
        this.rutas = rutas;
    }

    @Transactional(readOnly = true)
    public int minutos(Long rutaId, double latitud, double longitud) {
        ParametrosDeRuta fila = parametros.findById(rutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("ParametrosDeRuta", rutaId));
        List<PuntoDeParada> paradas = rutas.buscarConParadas(rutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId))
                .getParadas()
                .stream()
                .map(parada -> new PuntoDeParada(
                        parada.getOrden(),
                        Geo.latitud(parada.getUbicacion()),
                        Geo.longitud(parada.getUbicacion())))
                .toList();
        return calculador.minutos(
                fila, paradas, latitud, longitud, ZonedDateTime.now(reloj));
    }
}
