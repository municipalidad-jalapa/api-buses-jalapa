package gt.muni.jalapa.ecoruta.notificaciones.servicio;

/** Fallo al hablar con FCM. El evaluador lo registra y sigue. */
public class EnvioDeAvisoException extends RuntimeException {

    public EnvioDeAvisoException(String mensaje) {
        super(mensaje);
    }

    public EnvioDeAvisoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
