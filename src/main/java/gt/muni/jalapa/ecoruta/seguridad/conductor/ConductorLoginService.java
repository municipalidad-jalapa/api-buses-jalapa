package gt.muni.jalapa.ecoruta.seguridad.conductor;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PROVISIONAL — TODO(SCRUM-134). Ver {@link Usuario}.
 *
 * <p>Valida usuario, contrasena y rol, y emite el JWT. Vive aqui y no en el
 * controller porque la transaccion de lectura y el bcrypt no son asunto HTTP.
 */
@Service
@RequiredArgsConstructor
public class ConductorLoginService {

    /**
     * Hash de un valor constante, usado como senuelo.
     *
     * <p>Cuando el usuario no existe o no esta activo igual se corre un bcrypt
     * contra este hash, para que el tiempo de respuesta no distinga "no existe"
     * de "la contrasena no coincide". Mismo criterio que {@code EquipoService}.
     */
    private static final String HASH_SENUELO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String ROL_CONDUCTOR = "CONDUCTOR";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final ConductorJwtService jwtService;

    @Transactional(readOnly = true)
    public String login(String username, String password) {
        var fila = usuarios.findByUsernameAndActivoTrue(username);
        String hash = fila.map(Usuario::getPasswordHash).orElse(HASH_SENUELO);
        boolean passwordOk = passwordEncoder.matches(password, hash);
        Usuario conductor = fila.filter(u -> ROL_CONDUCTOR.equals(u.getRol())).orElse(null);
        if (!passwordOk || conductor == null) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        return jwtService.emitir(conductor.getUsername());
    }
}
