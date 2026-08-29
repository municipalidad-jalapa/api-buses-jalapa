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
        return tipo == TipoAviso.APROXIMACION
                ? "El bus esta por llegar"
                : "¿Lograste subir al bus?";
    }

    public String cuerpo() {
        return tipo == TipoAviso.APROXIMACION
                ? "El bus se acerca a " + paradaNombre + ". Preparate."
                : "Confirma si subiste en " + paradaNombre + ".";
    }
}
