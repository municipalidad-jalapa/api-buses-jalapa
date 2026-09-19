package gt.muni.jalapa.ecoruta.eta.web.dto;

/**
 * Situacion del bus al calcular el ETA. Tambien sirve a QA (HU-84) para agrupar
 * el error de las predicciones por categoria.
 */
public enum EstadoDelBus {
    /** Sobre el trazado y avanzando. */
    EN_RUTA,
    /** Detenido dentro del radio de una parada. */
    DETENIDO_EN_PARADA,
    /** Detenido fuera de parada mas del maximo configurado: no hay estimacion. */
    DETENIDO_FUERA_DE_PARADA,
    /** Fuera del trazado: el ETA se recalcula por el camino de reincorporacion. */
    EN_DESVIO,
    /** Sin bus, sin posicion, sin trazado o con la posicion vieja. */
    SIN_DATOS
}
