package gt.muni.jalapa.ecoruta.common;

/** Violacion de una regla de negocio (ej. doble registro en parada). Mapea a HTTP 422. */
public class ReglaDeNegocioException extends RuntimeException {
    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
