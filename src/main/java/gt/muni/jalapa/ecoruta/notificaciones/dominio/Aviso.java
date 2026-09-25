package gt.muni.jalapa.ecoruta.notificaciones.dominio;

/**
 * Payload que sale por el puerto {@code EnviadorDeNotificaciones}.
 * El token puede ir vacio: el enviador lo trata como fallo de envio.
 */
public record Aviso(
        Long reservaId,
        String dispositivoId,
        String tokenNotificacion,
        TipoAviso tipo,
        Long paradaId,
        String paradaNombre) {

    public String titulo() {
        return switch (tipo) {
            case APROXIMACION -> "El bus está por llegar";
            case LLEGADA -> "¿Lograste subir al bus?";
            case POR_VENCER -> "Tu aviso está por vencer";
        };
    }

    public String cuerpo() {
        return switch (tipo) {
            case APROXIMACION -> "El bus se acerca a " + paradaNombre + ". Preparate.";
            case LLEGADA -> "Confirmá si subiste en " + paradaNombre + ".";
            case POR_VENCER -> "¿Seguís esperando en " + paradaNombre
                    + "? Abrí EcoRuta y tocá «Sigo esperando» para guardar tu lugar.";
        };
    }
}
