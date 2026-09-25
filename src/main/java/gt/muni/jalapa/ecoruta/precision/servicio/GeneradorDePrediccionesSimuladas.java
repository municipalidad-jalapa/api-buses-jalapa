package gt.muni.jalapa.ecoruta.precision.servicio;

import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import gt.muni.jalapa.ecoruta.precision.repositorio.PrediccionEtaRepository;

import java.time.Instant;

/**
 * DATO DE PRUEBA (HU-73). No es una funcionalidad de producto.
 *
 * <p>HU-71 (calculo real del ETA) no esta disponible aqui. Este generador solo
 * escribe una prediccion marcada {@code simulada=true} para poder ejercitar
 * el almacenamiento y la medicion. No calcula un ETA real.
 */
public class GeneradorDePrediccionesSimuladas {

    private final PrediccionEtaRepository predicciones;

    public GeneradorDePrediccionesSimuladas(PrediccionEtaRepository predicciones) {
        this.predicciones = predicciones;
    }

    public PrediccionEta simular(Long rutaId, Long paradaId, Long vehiculoId,
                                 int etaPredichoMin, Instant predichoEn) {
        return predicciones.save(new PrediccionEta(
                rutaId, paradaId, vehiculoId, etaPredichoMin, predichoEn, true));
    }
}
