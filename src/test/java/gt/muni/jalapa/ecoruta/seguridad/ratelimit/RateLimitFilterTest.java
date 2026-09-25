package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.seguridad.RutasPublicas;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU Desarrollo-95: RateLimitFilter aislado, sin Spring ni Docker. Usa
 * MockHttpServletRequest/Response (spring-test) en vez de mocks a mano: son
 * servlets reales de mentira, no hace falta simular getWriter()/getStatus().
 */
class RateLimitFilterTest {

    private static final Instant AHORA = Instant.parse("2026-09-18T10:00:00Z");
    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    private CadenaContadora cadena;

    @BeforeEach
    void armarCadena() {
        cadena = new CadenaContadora();
    }

    @Test
    void una_ruta_no_protegida_nunca_se_limita() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(true, 1, 60, 1, 60));

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest peticion = get("/api/v1/conductor/panel");
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilter(peticion, respuesta, cadena);
            assertThat(respuesta.getStatus()).isEqualTo(200);
        }
        assertThat(cadena.invocaciones).isEqualTo(5);
    }

    @Test
    void una_ruta_protegida_responde_429_al_superar_el_limite_por_ip() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(true, 2, 60, 100, 60));

        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);

        MockHttpServletResponse tercera = new MockHttpServletResponse();
        filtro.doFilter(get("/api/v1/rutas"), tercera, cadena);

        assertThat(tercera.getStatus()).isEqualTo(429);
        assertThat(tercera.getHeader("Retry-After")).isNotNull();
        assertThat(tercera.getContentType()).contains("application/json");
        assertThat(tercera.getContentAsString()).contains("\"status\":429");
        assertThat(cadena.invocaciones).isEqualTo(2);
    }

    @Test
    void ips_distintas_tienen_cupos_independientes() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(true, 1, 60, 100, 60));

        MockHttpServletRequest primeraIp = get("/api/v1/rutas");
        primeraIp.setRemoteAddr("10.0.0.1");
        assertThat(status(filtro, primeraIp)).isEqualTo(200);

        MockHttpServletRequest mismaIpDeNuevo = get("/api/v1/rutas");
        mismaIpDeNuevo.setRemoteAddr("10.0.0.1");
        assertThat(status(filtro, mismaIpDeNuevo)).isEqualTo(429);

        MockHttpServletRequest otraIp = get("/api/v1/rutas");
        otraIp.setRemoteAddr("10.0.0.2");
        assertThat(status(filtro, otraIp)).isEqualTo(200);
    }

    @Test
    void dispositivos_distintos_tienen_cupos_independientes_aunque_compartan_ip() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(true, 100, 60, 1, 60));

        MockHttpServletRequest dispositivoA1 = post("/api/v1/reservas");
        dispositivoA1.addHeader(CABECERA_DISPOSITIVO, "dispositivo-a");
        assertThat(status(filtro, dispositivoA1)).isEqualTo(200);

        MockHttpServletRequest dispositivoA2 = post("/api/v1/reservas");
        dispositivoA2.addHeader(CABECERA_DISPOSITIVO, "dispositivo-a");
        assertThat(status(filtro, dispositivoA2)).isEqualTo(429);

        MockHttpServletRequest dispositivoB = post("/api/v1/reservas");
        dispositivoB.addHeader(CABECERA_DISPOSITIVO, "dispositivo-b");
        assertThat(status(filtro, dispositivoB)).isEqualTo(200);
    }

    @Test
    void sin_cabecera_de_dispositivo_solo_aplica_el_limite_por_ip() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(true, 100, 60, 1, 60));

        // GET /api/v1/rutas nunca trae X-Dispositivo-Id: no debe fallar por su ausencia.
        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
    }

    @Test
    void deshabilitado_no_limita_nada() throws Exception {
        RateLimitFilter filtro = filtro(propiedades(false, 1, 60, 1, 60));

        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
        assertThat(status(filtro, get("/api/v1/rutas"))).isEqualTo(200);
    }

    @Test
    void las_rutas_de_cupo_estricto_se_cortan_por_ip_aunque_cambie_el_dispositivo() throws Exception {
        RateLimitFilter filtro = filtro(new RateLimitProperties(true, 100, 60, 100, 60, 2, 600));

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest peticion = post("/api/v1/opiniones");
            peticion.addHeader(CABECERA_DISPOSITIVO, "evasion-" + i);
            assertThat(status(filtro, peticion)).isEqualTo(200);
        }
        MockHttpServletRequest tercera = post("/api/v1/opiniones");
        tercera.addHeader(CABECERA_DISPOSITIVO, "evasion-nuevo");
        assertThat(status(filtro, tercera)).isEqualTo(429);

        // Cupo propio por ruta: iniciar sesion sigue disponible desde la misma IP.
        assertThat(status(filtro, post("/api/v1/sesion/pasajero"))).isEqualTo(200);
        // Y una ruta de cupo general no usa el estricto.
        assertThat(status(filtro, post("/api/v1/reservas"))).isEqualTo(200);
    }

    @Test
    void toda_ruta_del_catalogo_publico_queda_limitada() throws Exception {
        for (RutasPublicas.Ruta ruta : RutasPublicas.TODAS) {
            RateLimitFilter filtro = filtro(new RateLimitProperties(true, 1, 60, 100, 60, 100, 600));
            String metodo = ruta.metodo() == null ? "GET" : ruta.metodo().name();
            String uri = ruta.patron().replace("**", "x").replace("*", "1");

            MockHttpServletRequest primera = new MockHttpServletRequest(metodo, uri);
            MockHttpServletRequest segunda = new MockHttpServletRequest(metodo, uri);
            assertThat(status(filtro, primera)).as(metodo + " " + uri).isEqualTo(200);
            assertThat(status(filtro, segunda)).as(metodo + " " + uri).isEqualTo(429);
        }
    }

    private RateLimitFilter filtro(RateLimitProperties propiedades) {
        return new RateLimitFilter(propiedades, json, reloj);
    }

    private static RateLimitProperties propiedades(boolean habilitado, int capacidadIp, int ventanaIp,
                                                    int capacidadDispositivo, int ventanaDispositivo) {
        return new RateLimitProperties(habilitado, capacidadIp, ventanaIp, capacidadDispositivo, ventanaDispositivo,
                1000, 600);
    }

    private int status(RateLimitFilter filtro, MockHttpServletRequest peticion) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta.getStatus();
    }

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", uri);
        peticion.setRemoteAddr("127.0.0.1");
        return peticion;
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", uri);
        peticion.setRemoteAddr("127.0.0.1");
        return peticion;
    }

    /** Cuenta cuantas veces la cadena real siguio adelante y responde 200. */
    private static final class CadenaContadora implements FilterChain {
        int invocaciones;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            invocaciones++;
            ((jakarta.servlet.http.HttpServletResponse) response).setStatus(200);
        }
    }
}
