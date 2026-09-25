package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite y lee el JWT propio del backend. El idToken de Firebase no se reenvia
 * al cliente: este es el token que autoriza el panel del conductor y el panel
 * municipal (SCRUM-173). Un solo mecanismo; lo que cambia es el claim de rol y
 * cuanto dura la sesion.
 */
@Component
@RequiredArgsConstructor
public class EmisorDeJwt {

    public static final String CLAIM_ROL = "rol";
    public static final String ROL_CONDUCTOR = "conductor";
    public static final String ROL_ADMIN = "admin";

    /** SCRUM-26, bloque D. Manda mas que el admin: administra el sistema. */
    public static final String ROL_SUPERADMIN = "superadmin";

    private final JwtProperties jwt;

    public SesionJwt emitirParaConductor(String subject) {
        return emitir(subject, ROL_CONDUCTOR, jwt.duracion());
    }

    public SesionJwt emitir(String subject, String rol, Duration duracion) {
        Instant ahora = Instant.now();
        Instant expiraEn = ahora.plus(duracion);
        String token = Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ROL, rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .signWith(jwt.clave())
                .compact();
        return new SesionJwt(token, expiraEn, rol, subject);
    }

    /**
     * Lee un JWT de conductor. Vacio si falta, esta mal firmado, vencio o no
     * lleva el rol de conductor. Nunca lanza: el filtro deja el contexto vacio
     * y la autorizacion responde 401.
     */
    public Optional<SesionJwt> leerConductor(String token) {
        return leer(token, ROL_CONDUCTOR);
    }

    /** Igual que {@link #leerConductor}, para el rol pedido. */
    public Optional<SesionJwt> leer(String token, String rol) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwt.clave())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!rol.equals(claims.get(CLAIM_ROL, String.class))) {
                return Optional.empty();
            }
            Instant expiraEn = claims.getExpiration().toInstant();
            return Optional.of(new SesionJwt(token, expiraEn, rol, claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
