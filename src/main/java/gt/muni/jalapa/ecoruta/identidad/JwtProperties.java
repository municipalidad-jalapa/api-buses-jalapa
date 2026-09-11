package gt.muni.jalapa.ecoruta.identidad;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Firma y vencimiento del JWT propio del backend.
 *
 * @param secret           HMAC-SHA256; minimo 32 caracteres. Viene de {@code JWT_SECRET}
 * @param duracionMinutos  jornada laboral (8-12 h). Por defecto 480 = 8 h
 */
@ConfigurationProperties("ecoruta.jwt")
public record JwtProperties(String secret, int duracionMinutos) {

    public static final int LARGO_MINIMO_SECRETO = 32;

    public JwtProperties {
        if (!StringUtils.hasText(secret) || secret.length() < LARGO_MINIMO_SECRETO) {
            throw new IllegalStateException(
                    "ecoruta.jwt.secret debe tener al menos %d caracteres"
                            .formatted(LARGO_MINIMO_SECRETO));
        }
        if (duracionMinutos <= 0) {
            duracionMinutos = 480;
        }
    }

    public Duration duracion() {
        return Duration.ofMinutes(duracionMinutos);
    }

    public SecretKey clave() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
