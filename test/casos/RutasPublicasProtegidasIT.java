package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU Desarrollo-95, criterio 1: todo endpoint publico de negocio tiene que
 * pasar por el limite de peticiones.
 *
 * <p>Las rutas publicas viven en dos sitios que no se hablan entre si:
 * {@code SecurityConfig} (quien puede entrar sin credencial) y
 * {@code RateLimitFilter.RUTAS_PROTEGIDAS} (a quien se le limita). QA encontro
 * asi que {@code POST /api/v1/auth/admin}, publico desde SCRUM-173, quedo sin
 * limite: alguien lo agrego a una lista y no a la otra.
 *
 * <p>Esta prueba no compara las dos listas a mano. Recorre los endpoints
 * reales de los controladores, le pregunta a la autorizacion real de Spring
 * Security si un anonimo pasa, y exige que el filtro cubra cada uno que pase.
 * Asi tambien atrapa una ruta nueva declarada solo en un controlador.
 * Falla con el nombre exacto del endpoint que falta.
 */
class RutasPublicasProtegidasIT extends IntegracionPostgisTest {

    private static final List<RequestMethod> METODOS_A_PROBAR = List.of(
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE);

    /** Boot registra otro mapeo (controllerEndpointHandlerMapping) para actuator. */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeoDeControladores;

    @Autowired
    private FilterChainProxy cadenasDeSeguridad;

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RateLimitFilter filtro;

    @Test
    void todo_endpoint_publico_de_negocio_esta_cubierto_por_el_limite_de_peticiones() {
        Set<String> publicos = new TreeSet<>();
        Set<String> sinLimite = new TreeSet<>();

        for (var entrada : mapeoDeControladores.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entrada.getKey();
            HandlerMethod manejador = entrada.getValue();

            for (String patron : patronesDe(info)) {
                if (!patron.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod metodo : metodosDe(info)) {
                    MockHttpServletRequest peticion = construirPeticion(
                            metodo, patron.replaceAll("\\{[^}]+}", "1"));

                    if (!laEntradaEsAnonima(peticion)) {
                        continue;
                    }
                    String etiqueta = metodo + " " + patron
                            + "  (" + manejador.getBeanType().getSimpleName()
                            + "#" + manejador.getMethod().getName() + ")";
                    publicos.add(etiqueta);
                    if (!filtro.esRutaProtegida(peticion)) {
                        sinLimite.add(etiqueta);
                    }
                }
            }
        }

        // Sin esto la prueba pasaria en vacio si la introspeccion dejara de ver rutas.
        assertThat(publicos)
                .as("la prueba tiene que ver los endpoints publicos conocidos")
                .anyMatch(e -> e.startsWith("POST /api/v1/reservas "))
                .anyMatch(e -> e.startsWith("GET /api/v1/rutas "));

        assertThat(sinLimite)
                .as("Endpoints publicos SIN limite de peticiones. Agregalos a "
                        + "RateLimitFilter.RUTAS_PROTEGIDAS (o, si es a proposito, "
                        + "documenta por que no en esta prueba)")
                .isEmpty();
    }

    /**
     * Igual que la arma MockMvc. Un MockHttpServletRequest suelto no basta:
     * el MvcRequestMatcher de Spring Security necesita el ServletContext real
     * para resolver las rutas y, sin el, rechaza hasta las publicas.
     */
    private MockHttpServletRequest construirPeticion(RequestMethod metodo, String ruta) {
        return MockMvcRequestBuilders
                .request(HttpMethod.valueOf(metodo.name()), ruta)
                .buildRequest(contexto.getServletContext());
    }

    private Set<String> patronesDe(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition().getPatterns();
    }

    private Set<RequestMethod> metodosDe(RequestMappingInfo info) {
        Set<RequestMethod> declarados = info.getMethodsCondition().getMethods();
        return declarados.isEmpty() ? new TreeSet<>(METODOS_A_PROBAR) : declarados;
    }

    /** Pregunta a la cadena que de verdad atenderia la peticion si un anonimo puede entrar. */
    private boolean laEntradaEsAnonima(MockHttpServletRequest peticion) {
        SecurityFilterChain cadena = cadenasDeSeguridad.getFilterChains().stream()
                .filter(c -> c.matches(peticion))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Ninguna cadena de seguridad atiende " + peticion.getMethod()
                                + " " + peticion.getRequestURI()));

        AuthorizationFilter autorizacion = cadena.getFilters().stream()
                .filter(AuthorizationFilter.class::isInstance)
                .map(AuthorizationFilter.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "La cadena de " + peticion.getRequestURI() + " no tiene AuthorizationFilter"));

        Authentication anonimo = new AnonymousAuthenticationToken(
                "prueba", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        AuthorizationResult resultado = autorizacion.getAuthorizationManager()
                .authorize(() -> anonimo, peticion);
        return resultado != null && resultado.isGranted();
    }
}
