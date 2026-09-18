package gt.muni.jalapa.ecoruta.identidad.servicio;

import java.time.Instant;

/** JWT propio del backend ya firmado, listo para devolver al conductor. */
public record SesionJwt(String token, Instant expiraEn, String rol, String subject) {
}
