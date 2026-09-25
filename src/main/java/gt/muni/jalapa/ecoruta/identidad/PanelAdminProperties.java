package gt.muni.jalapa.ecoruta.identidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Panel web municipal (SCRUM-173, HU Desarrollo-78).
 *
 * <p>Prefijo propio y no {@code ecoruta.admin}: ese es del token provisional de
 * SCRUM-142, que se borra entero con SCRUM-134.
 *
 * @param inactividadMinutos     sin actividad durante este plazo la sesion se
 *                               cierra: el JWT del admin dura esto y se renueva
 *                               mientras se usa el panel
 * @param datosRecientesSegundos una posicion mas vieja se muestra como "sin
 *                               datos recientes"
 * @param firebaseUidInicial     uid de Firebase de la primera cuenta de
 *                               administrador. Vacio = no se crea ninguna
 * @param usuarioInicial         nombre local de esa cuenta
 * @param superadminFirebaseUid  uid de Firebase del SuperAdmin (SCRUM-26,
 *                               bloque D). La cuenta la crea la migracion V21
 *                               sin uid; aqui se le enlaza el de cada entorno
 */
@ConfigurationProperties("ecoruta.panel-admin")
public record PanelAdminProperties(int inactividadMinutos, int datosRecientesSegundos,
                                   String firebaseUidInicial, String usuarioInicial,
                                   String superadminFirebaseUid) {

    public PanelAdminProperties {
        inactividadMinutos = inactividadMinutos <= 0 ? 30 : inactividadMinutos;
        datosRecientesSegundos = datosRecientesSegundos <= 0 ? 120 : datosRecientesSegundos;
        usuarioInicial = StringUtils.hasText(usuarioInicial) ? usuarioInicial : "admin";
    }

    public Duration inactividad() {
        return Duration.ofMinutes(inactividadMinutos);
    }

    public Duration datosRecientes() {
        return Duration.ofSeconds(datosRecientesSegundos);
    }

    public boolean hayCuentaInicial() {
        return StringUtils.hasText(firebaseUidInicial);
    }

    public boolean haySuperAdminInicial() {
        return StringUtils.hasText(superadminFirebaseUid);
    }
}
