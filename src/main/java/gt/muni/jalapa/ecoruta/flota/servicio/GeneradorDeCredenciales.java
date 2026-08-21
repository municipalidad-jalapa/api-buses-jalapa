package gt.muni.jalapa.ecoruta.flota.servicio;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Genera credenciales de equipo. El secreto en claro sale de aqui una sola vez. */
@Component
@RequiredArgsConstructor
public class GeneradorDeCredenciales {

    /**
     * Deliberadamente {@code new SecureRandom()} y no {@code getInstanceStrong()}:
     * en contenedores Linux este ultimo puede quedarse bloqueado esperando
     * entropia de /dev/random. La fuente por defecto ya es criptografica.
     */
    private static final SecureRandom RNG = new SecureRandom();

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final PasswordEncoder passwordEncoder;

    public CredencialEmitida generar() {
        String codigoPublico = TokenDeEquipoFormato.PREFIJO + aleatorio(
                TokenDeEquipoFormato.BYTES_CODIGO_PUBLICO);
        String secreto = aleatorio(TokenDeEquipoFormato.BYTES_SECRETO);
        return new CredencialEmitida(codigoPublico, secreto, passwordEncoder.encode(secreto));
    }

    private static String aleatorio(int bytes) {
        byte[] material = new byte[bytes];
        RNG.nextBytes(material);
        return B64.encodeToString(material);
    }
}
