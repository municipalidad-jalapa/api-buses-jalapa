package gt.muni.jalapa.ecoruta.precision.servicio;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Criterios objetivos para aceptar o rechazar el ETA (HU-73, criterio 4).
 *
 * <p>El margen tolerable es configurable ({@code ecoruta.eta.margen-error-tolerable-min}).
 * El numero final NO se inventa: queda pendiente de la Municipalidad. Sin ese
 * numero, {@link #acepta(double)} no opina.
 */
@Component
public class CriterioDeEvaluacionEta {

    private final EtaPrecisionProperties propiedades;

    public CriterioDeEvaluacionEta(EtaPrecisionProperties propiedades) {
        this.propiedades = propiedades;
    }

    public CasoOperativoEta clasificar(boolean hayPosicion, boolean dentroDeGeocerca) {
        if (!hayPosicion) {
            return CasoOperativoEta.SIN_DATOS_RECIENTES;
        }
        return dentroDeGeocerca ? CasoOperativoEta.BUS_DETENIDO : CasoOperativoEta.BUS_EN_RUTA;
    }

    /** Solo el bus detenido en geocerca produce una llegada medible. */
    public boolean registraLlegada(CasoOperativoEta caso) {
        return caso == CasoOperativoEta.BUS_DETENIDO;
    }

    /**
     * empty = margen aun no confirmado por la Municipalidad.
     * true/false = la muestra queda dentro o fuera del margen acordado.
     */
    public Optional<Boolean> acepta(double errorMin) {
        if (!propiedades.margenConfirmado()) {
            return Optional.empty();
        }
        return Optional.of(errorMin <= propiedades.margenErrorTolerableMin());
    }
}
