package gt.muni.jalapa.ecoruta.seguridad.conductor;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link Usuario}.
 *
 * <p>Firma y verifica el JWT de conductor con {@code ecoruta.jwt.secret} y
 * {@code ecoruta.jwt.duracion-minutos} (ya en {@code application.yml}; no se
 * agregan propiedades). La clave se deriva una vez al construir el bean:
 * {@code Keys.hmacShaKeyFor} exige >= 256 bits y fallar al arranque es
 * mejor que firmar con un secreto corto en cada peticion.
 *
 * <p>{@link #validarYObtenerUsername(String)} nunca lanza: token ausente,
 * mal formado, con firma invalida o vencido es {@code Optional.empty()}.
 * Quien responde 401 es la capa de autorizacion, igual que
 * {@code EquipoService.autenticar}.
 */
@Service
public class ConductorJwtService {

    private static final String CLAIM_ROL = "rol";
    private static final String ROL_CONDUCTOR = "CONDUCTOR";

    private final SecretKey clave;
    private final Duration duracion;

    public ConductorJwtService(
            @Value("${ecoruta.jwt.secret}") String secreto,
            @Value("${ecoruta.jwt.duracion-minutos}") int duracionMinutos) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(duracionMinutos);
    }

    public String emitir(String username) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROL, ROL_CONDUCTOR)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    public Optional<String> validarYObtenerUsername(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String username = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(username);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
