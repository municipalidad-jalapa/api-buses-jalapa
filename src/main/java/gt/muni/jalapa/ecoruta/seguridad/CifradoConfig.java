package gt.muni.jalapa.ecoruta.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El cifrador vive aparte de SecurityConfig porque no es cosa de la cadena de
 * filtros: lo usa flota/ para el hash de la credencial del equipo, y sigue
 * haciendo falta aunque la cadena cambie por completo con SCRUM-134.
 */
@Configuration
public class CifradoConfig {

    /**
     * Coste 10, el mismo de los hashes ya sembrados en V3.
     *
     * <p>Son unos 50-80 ms de CPU por verificacion. Con un bus mandando un lote
     * cada pocos segundos es invisible, y es el precio deliberado de que revocar
     * surta efecto de inmediato: se verifica contra la base en cada peticion, sin
     * cache.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
