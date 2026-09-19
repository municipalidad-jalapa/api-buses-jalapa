package gt.muni.jalapa.ecoruta.precision.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.precision.dominio.LlegadaReal;
import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import gt.muni.jalapa.ecoruta.precision.repositorio.LlegadaRealRepository;
import gt.muni.jalapa.ecoruta.precision.repositorio.ParadasParaPrecisionRepository;
import gt.muni.jalapa.ecoruta.precision.repositorio.PrediccionEtaRepository;
import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.Optional;

/**
 * Detecta la llegada real a una parada a partir de la telemetria (HU-73).
 *
 * <p>La proximidad es la geocerca ya existente del proyecto (150 m). No se
 * toca el calculo del ETA. Se escucha el evento que la ingesta ya publica.
 */
@Service
public class DetectorDeLlegadaReal {

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    private final PrediccionEtaRepository predicciones;
    private final LlegadaRealRepository llegadas;
    private final ParadasParaPrecisionRepository paradas;
    private final VehiculoRepository vehiculos;
    private final EtaPrecisionProperties propiedades;
    private final CriterioDeEvaluacionEta criterio;

    public DetectorDeLlegadaReal(PrediccionEtaRepository predicciones,
                                 LlegadaRealRepository llegadas,
                                 ParadasParaPrecisionRepository paradas,
                                 VehiculoRepository vehiculos,
                                 EtaPrecisionProperties propiedades,
                                 CriterioDeEvaluacionEta criterio) {
        this.predicciones = predicciones;
        this.llegadas = llegadas;
        this.paradas = paradas;
        this.vehiculos = vehiculos;
        this.propiedades = propiedades;
        this.criterio = criterio;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPosicionVigente(PosicionVigenteActualizada evento) {
        detectar(evento.posicion());
    }

    @Transactional
    public Optional<LlegadaReal> detectar(PosicionActualResponse posicion) {
        if (posicion == null || posicion.vehiculo() == null) {
            return Optional.empty();
        }
        return vehiculos.findByIdentificador(posicion.vehiculo())
                .flatMap(vehiculo -> evaluar(posicion, vehiculo));
    }

    private Optional<LlegadaReal> evaluar(PosicionActualResponse posicion, Vehiculo vehiculo) {
        Optional<Parada> cercana = paradaDentroDeGeocerca(
                posicion.latitud(), posicion.longitud(), vehiculo.getRutaId());
        CasoOperativoEta caso = criterio.clasificar(true, cercana.isPresent());
        if (!criterio.registraLlegada(caso)) {
            return Optional.empty();
        }

        Parada parada = cercana.orElseThrow();
        Optional<PrediccionEta> vigente = predicciones.vigente(
                parada.getRuta().getId(), parada.getId(), vehiculo.getId(), posicion.timestamp());
        if (vigente.isEmpty()) {
            return Optional.empty();
        }

        PrediccionEta prediccion = vigente.get();
        double error = CalculadorDeError.minutos(
                prediccion.getPredichoEn(), prediccion.getEtaPredichoMin(), posicion.timestamp());
        return Optional.of(llegadas.save(new LlegadaReal(prediccion, posicion.timestamp(), error)));
    }

    private Optional<Parada> paradaDentroDeGeocerca(double latitud, double longitud, Long rutaId) {
        int radio = propiedades.geocercaMetros();
        return paradas.todasConRuta().stream()
                .filter(parada -> rutaId == null || rutaId.equals(parada.getRuta().getId()))
                .filter(parada -> metros(latitud, longitud,
                        Geo.latitud(parada.getUbicacion()),
                        Geo.longitud(parada.getUbicacion())) <= radio)
                .min(Comparator.comparingDouble(parada -> metros(latitud, longitud,
                        Geo.latitud(parada.getUbicacion()),
                        Geo.longitud(parada.getUbicacion()))));
    }

    static double metros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
