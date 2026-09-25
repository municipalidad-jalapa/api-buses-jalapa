package gt.muni.jalapa.ecoruta.calles;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del enrutado por calles (SCRUM-26, bloque C).
 *
 * @param habilitada       con false se vuelve a la estimacion por factor del
 *                         ETA, sin tocar codigo: util si la red todavia no
 *                         esta importada en un entorno
 * @param radioNodoMetros  a mas de esta distancia del bus (o del punto de
 *                         reincorporacion) no hay calle que valga: se
 *                         considera que no existe camino
 * @param candidatos       cuantos puntos del trazado por delante se prueban
 *                         como reincorporacion. Se toma el que da el camino
 *                         por calles mas corto, no el mas cercano en recta
 */
@ConfigurationProperties("ecoruta.red-de-calles")
public record RedDeCallesProperties(boolean habilitada, int radioNodoMetros, int candidatos) {

    public RedDeCallesProperties {
        radioNodoMetros = radioNodoMetros <= 0 ? 150 : radioNodoMetros;
        candidatos = candidatos <= 0 ? 12 : candidatos;
    }
}
