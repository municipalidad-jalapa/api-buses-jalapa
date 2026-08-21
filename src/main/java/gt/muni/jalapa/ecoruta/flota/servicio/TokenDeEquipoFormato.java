package gt.muni.jalapa.ecoruta.flota.servicio;

import java.util.regex.Pattern;

/**
 * Formato del token que presenta el equipo a bordo:
 * {@code eq_<codigoPublico>.<secreto>}.
 *
 * <p>Es de dos partes porque bcrypt lleva sal: no se puede consultar la fila por
 * el hash del secreto presentado. El codigo publico localiza la fila y el
 * secreto se verifica contra su hash.
 *
 * <p>El prefijo {@code eq_} no es decorativo. SCRUM-134 va a poner un JWT de
 * Firebase en la MISMA cabecera Authorization; el prefijo deja que el filtro del
 * equipo decida en O(1), sin ir a la base y sin lanzar, si el bearer es suyo.
 * Lo que no calce se deja intacto para que el filtro de Firebase lo vea. Asi los
 * dos filtros conviven sin coordinarse.
 *
 * <p>El separador es un punto porque el alfabeto Base64URL
 * ({@code A-Z a-z 0-9 - _}) lo excluye: partir por el primer punto es inequivoco.
 */
public final class TokenDeEquipoFormato {

    public static final String PREFIJO = "eq_";
    public static final char SEPARADOR = '.';

    /** 9 bytes aleatorios en Base64URL sin relleno. */
    public static final int BYTES_CODIGO_PUBLICO = 9;
    public static final int LARGO_CODIGO_PUBLICO = PREFIJO.length() + 12;

    /**
     * 32 bytes (256 bits) en Base64URL sin relleno = 43 caracteres.
     *
     * <p>Con el codigo publico el token llega a 59 caracteres, comodamente bajo
     * el limite de 72 bytes de bcrypt: un secreto mas largo lo truncaria en
     * silencio y dos secretos distintos podrian validar igual.
     */
    public static final int BYTES_SECRETO = 32;
    public static final int LARGO_SECRETO = 43;

    /** Largo exacto del token completo. Se valida antes de tocar la base. */
    public static final int LARGO_TOKEN = LARGO_CODIGO_PUBLICO + 1 + LARGO_SECRETO;

    public static final Pattern PATRON = Pattern.compile(
            "^eq_[A-Za-z0-9_-]{12}\\.[A-Za-z0-9_-]{43}$");

    private TokenDeEquipoFormato() {
    }
}
