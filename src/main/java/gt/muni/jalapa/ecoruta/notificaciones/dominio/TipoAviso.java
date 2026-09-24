package gt.muni.jalapa.ecoruta.notificaciones.dominio;

/**
 * Los avisos que recibe el pasajero.
 *
 * <p>{@link #codigoWeb()} es el valor de {@code data.tipo} en el push: el que
 * entienden el Service Worker y la app web ({@code mensajeria.ts}). Antes se
 * mandaba el nombre del enum, la web no lo reconocia y descartaba el aviso
 * (QA, Ecoruta_DESARROLLO 4.2).
 */
public enum TipoAviso {
    /** El bus entro al radio de aproximacion de la parada reservada. */
    APROXIMACION("bus-cerca"),
    /** El bus llego a la parada: se pregunta si el pasajero subio. */
    LLEGADA("confirmar-abordaje"),
    /** QA 4.1: a la reserva le queda poco; se ofrece renovarla. */
    POR_VENCER("reserva-por-vencer");

    private final String codigoWeb;

    TipoAviso(String codigoWeb) {
        this.codigoWeb = codigoWeb;
    }

    public String codigoWeb() {
        return codigoWeb;
    }
}
