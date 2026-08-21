package gt.muni.jalapa.ecoruta.flota.servicio;

/**
 * Una credencial recien generada, con el secreto todavia en claro.
 *
 * <p>Objeto de paso: existe entre el generador y la respuesta HTTP del alta, y
 * en ningun otro sitio. El secreto no se persiste ni se puede volver a obtener.
 *
 * @param codigoPublico parte publica; identifica la fila y se puede loguear
 * @param secreto       parte secreta, en claro. Se muestra UNA sola vez
 * @param secretoHash   bcrypt del secreto; es lo unico que llega a la base
 */
public record CredencialEmitida(String codigoPublico, String secreto, String secretoHash) {

    /** Lo que se entrega al equipo a bordo para que lo mande en Authorization. */
    public String credencialCompleta() {
        return codigoPublico + TokenDeEquipoFormato.SEPARADOR + secreto;
    }

    /**
     * Un record genera un toString() que imprime TODOS sus componentes, secreto
     * incluido. Sin esta sobreescritura, cualquier log.debug("{}", credencial)
     * volcaria una credencial viva (criterio (d) de SCRUM-142).
     */
    @Override
    public String toString() {
        return "CredencialEmitida[codigoPublico=%s, secreto=***, secretoHash=***]"
                .formatted(codigoPublico);
    }
}
