package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import gt.muni.jalapa.ecoruta.pasajeros.servicio.SesionDePasajero;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correccion de QA: vistas separadas para el pasajero y el personal. El
 * frontend saca el acceso del conductor y de la administracion del menu del
 * pasajero (ruta /tools); la barrera real es esta: cada grupo de rutas de la
 * API solo se abre con el rol que le corresponde.
 *
 * <p>Se pide una subruta que no existe: si la seguridad deja pasar la
 * respuesta es 404 (no hay handler); si no, 401 sin credencial o 403 con la
 * credencial de otro rol. Asi el resultado no depende de la logica de negocio
 * de cada endpoint (ruta asignada, cuenta existente, etc.).
 */
class AccesoPorRolIT extends IntegracionPostgisTest {

    private static final Duration DURACION = Duration.ofMinutes(10);

    @Autowired
    private EmisorDeJwt emisor;

    @ParameterizedTest(name = "{0} en {1} -> {2}")
    @CsvSource({
            // Panel del conductor: solo el conductor.
            "anonimo,    /api/v1/conductor/no-existe,  401",
            "pasajero,   /api/v1/conductor/no-existe,  403",
            "conductor,  /api/v1/conductor/no-existe,  404",
            "admin,      /api/v1/conductor/no-existe,  403",
            "superadmin, /api/v1/conductor/no-existe,  403",
            // Panel municipal: el admin y, por encima, el SuperAdmin.
            "anonimo,    /api/v1/admin/no-existe,      401",
            "pasajero,   /api/v1/admin/no-existe,      403",
            "conductor,  /api/v1/admin/no-existe,      403",
            "admin,      /api/v1/admin/no-existe,      404",
            "superadmin, /api/v1/admin/no-existe,      404",
            "anonimo,    /api/v1/panel/no-existe,      401",
            "pasajero,   /api/v1/panel/no-existe,      403",
            "conductor,  /api/v1/panel/no-existe,      403",
            "admin,      /api/v1/panel/no-existe,      404",
            "superadmin, /api/v1/panel/no-existe,      404",
            // Administracion del sistema: solo el SuperAdmin.
            "anonimo,    /api/v1/superadmin/no-existe, 401",
            "pasajero,   /api/v1/superadmin/no-existe, 403",
            "conductor,  /api/v1/superadmin/no-existe, 403",
            "admin,      /api/v1/superadmin/no-existe, 403",
            "superadmin, /api/v1/superadmin/no-existe, 404",
    })
    void cada_grupo_de_rutas_solo_se_abre_con_su_rol(String quien, String ruta, int esperado) throws Exception {
        mockMvc.perform(conCredencial(get(ruta), quien))
                .andExpect(status().is(esperado));
    }

    /**
     * El inicio de sesion del personal sigue abierto sin credencial: la
     * separacion de vistas no debe dejar a nadie sin poder entrar. Un cuerpo
     * vacio llega a la validacion (400), no a la seguridad (401/403).
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/conductor", "/api/v1/auth/admin"})
    void el_inicio_de_sesion_del_personal_se_alcanza_sin_credencial(String ruta) throws Exception {
        mockMvc.perform(post(ruta).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder conCredencial(MockHttpServletRequestBuilder peticion, String quien) {
        String token = switch (quien) {
            case "anonimo" -> null;
            case "pasajero" -> emisor.emitir("1", SesionDePasajero.ROL, DURACION).token();
            case "conductor" -> emisor.emitirParaConductor("uid-conductor").token();
            case "admin" -> emisor.emitir("uid-admin", EmisorDeJwt.ROL_ADMIN, DURACION).token();
            case "superadmin" -> emisor.emitir("uid-superadmin", EmisorDeJwt.ROL_SUPERADMIN, DURACION).token();
            default -> throw new IllegalArgumentException(quien);
        };
        return token == null ? peticion : peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
