package gt.muni.jalapa.ecoruta.precision.servicio;

/**
 * Los tres casos que el criterio 4 de HU-73 exige definir.
 *
 * <ul>
 *   <li>{@code BUS_DETENIDO}: la posicion cae dentro de la geocerca de una
 *       parada. Es una llegada real y se asocia a la prediccion vigente.</li>
 *   <li>{@code BUS_EN_RUTA}: hay telemetria reciente pero fuera de toda
 *       geocerca. No se registra llegada.</li>
 *   <li>{@code SIN_DATOS_RECIENTES}: no hay posicion que evaluar. No se
 *       inventa una llegada ni se cierra una prediccion.</li>
 * </ul>
 */
public enum CasoOperativoEta {
    BUS_DETENIDO,
    BUS_EN_RUTA,
    SIN_DATOS_RECIENTES
}
