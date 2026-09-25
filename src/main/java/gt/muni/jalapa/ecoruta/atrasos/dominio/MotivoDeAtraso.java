package gt.muni.jalapa.ecoruta.atrasos.dominio;

/**
 * Por que viene demorado el bus (SCRUM-26, bloque E). Dos motivos y no un texto
 * libre: el pasajero entiende de un vistazo, y el panel municipal puede contar.
 */
public enum MotivoDeAtraso {

    /** Congestion en la calle. */
    TRAFICO,

    /** Averia, choque, bloqueo: algo que paso y detiene al bus. */
    INCIDENTE;

    public static MotivoDeAtraso de(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return valueOf(texto.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
