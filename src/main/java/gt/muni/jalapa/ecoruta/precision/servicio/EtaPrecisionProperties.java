package gt.muni.jalapa.ecoruta.precision.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de evaluacion del ETA (HU-73, criterio 4).
 *
 * <p>{@code margenErrorTolerableMin} es el umbral para aceptar o rechazar una
 * muestra. Su valor FINAL esta PENDIENTE de confirmacion con la Municipalidad:
 * no se inventa aqui. Mientras sea {@code null}, el mecanismo esta definido
 * pero no acepta ni rechaza automaticamente.
 *
 * <p>{@code geocercaMetros} reutiliza el criterio de proximidad que ya existe
 * en el proyecto ({@code ecoruta.demanda.geocerca-metros}, 150 m). No se crea
 * un radio paralelo.
 */
@ConfigurationProperties("ecoruta.eta")
public record EtaPrecisionProperties(Integer margenErrorTolerableMin, int geocercaMetros) {

    public EtaPrecisionProperties {
        geocercaMetros = geocercaMetros <= 0 ? 150 : geocercaMetros;
    }

    /** false hasta que la Municipalidad confirme el numero. */
    public boolean margenConfirmado() {
        return margenErrorTolerableMin != null;
    }
}
