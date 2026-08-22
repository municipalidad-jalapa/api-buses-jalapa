package gt.muni.jalapa.ecoruta.flota.seguridad;

import gt.muni.jalapa.ecoruta.flota.servicio.TokenDeEquipoFormato;

import java.util.Optional;

/**
 * Token de equipo ya partido en sus dos mitades.
 *
 * <p>El parseo nunca lanza: una cabecera que no calza con el formato no es un
 * error, es simplemente un bearer de otro (un JWT de Firebase, por ejemplo) y
 * tiene que seguir su camino intacto.
 *
 * @param codigoPublico parte publica, segura de loguear
 * @param secreto       parte secreta; no se loguea nunca, ni truncada
 */
public record TokenDeEquipo(String codigoPublico, String secreto) {

    private static final String PREFIJO_BEARER = "Bearer ";

    /**
     * Parte la cabecera Authorization si -- y solo si -- contiene un token de
     * equipo bien formado.
     *
     * @return vacio si la cabecera falta, no es Bearer, o no calza con el formato
     */
    public static Optional<TokenDeEquipo> parsear(String cabeceraAuthorization) {
        if (cabeceraAuthorization == null
                || !cabeceraAuthorization.startsWith(PREFIJO_BEARER)) {
            return Optional.empty();
        }
        String valor = cabeceraAuthorization.substring(PREFIJO_BEARER.length()).trim();

        // Se corta por largo antes de aplicar el regex y antes de tocar la base:
        // asi una cabecera enorme no se convierte en trabajo del servidor.
        if (valor.length() != TokenDeEquipoFormato.LARGO_TOKEN
                || !TokenDeEquipoFormato.PATRON.matcher(valor).matches()) {
            return Optional.empty();
        }

        int corte = valor.indexOf(TokenDeEquipoFormato.SEPARADOR);
        return Optional.of(new TokenDeEquipo(
                valor.substring(0, corte), valor.substring(corte + 1)));
    }

    /** Un record imprimiria el secreto en su toString() generado. */
    @Override
    public String toString() {
        return "TokenDeEquipo[codigoPublico=%s, secreto=***]".formatted(codigoPublico);
    }
}
