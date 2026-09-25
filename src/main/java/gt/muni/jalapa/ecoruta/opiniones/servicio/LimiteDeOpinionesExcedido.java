package gt.muni.jalapa.ecoruta.opiniones.servicio;

/** El navegador ya mando el maximo de opiniones de la ventana: 429. */
public class LimiteDeOpinionesExcedido extends RuntimeException {

    public LimiteDeOpinionesExcedido() {
        super("Enviaste varias opiniones seguidas. Intenta de nuevo en unos minutos.");
    }
}
