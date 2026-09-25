package gt.muni.jalapa.ecoruta.seguridad;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;

/**
 * Catalogo unico de las rutas que se alcanzan sin credencial de la
 * municipalidad, del piloto ni del equipo a bordo (SCRUM-26, correccion de QA).
 *
 * <p>Antes habia dos listas a mano: los {@code permitAll()} de las cadenas de
 * seguridad y las rutas que limitaba {@code RateLimitFilter}. Se
 * desincronizaron —las opiniones y la sesion del pasajero quedaron abiertas y
 * sin limite por IP— y QA lo evadio cambiando {@code X-Dispositivo-Id} en cada
 * peticion. Ahora las cadenas abren sus rutas publicas con {@link #abrir} y el
 * filtro limita exactamente estas mismas: abrir una ruta aqui la deja limitada
 * en el mismo gesto.
 *
 * <p>{@link Cupo#ESTRICTO} marca las rutas que escriben algo por cada peticion
 * de un anonimo (una opinion, una sesion). Ademas del cupo general por IP,
 * tienen uno propio mucho menor: el identificador del dispositivo lo pone el
 * cliente y no sirve de defensa por si solo.
 */
public final class RutasPublicas {

    public enum Cupo { GENERAL, ESTRICTO }

    /**
     * @param metodo  null = cualquier metodo
     * @param anonima true si la ruta se abre con {@code permitAll()}; false si
     *                exige la sesion del pasajero pero igual se limita
     */
    public record Ruta(HttpMethod metodo, String patron, Cupo cupo, boolean anonima) {
    }

    public static final List<Ruta> TODAS = List.of(
            new Ruta(HttpMethod.GET, "/api/v1/telemetria/posicion", Cupo.GENERAL, true),
            new Ruta(HttpMethod.GET, "/api/v1/telemetria/stream", Cupo.GENERAL, true),
            new Ruta(HttpMethod.GET, "/api/v1/rutas", Cupo.GENERAL, true),
            new Ruta(HttpMethod.GET, "/api/v1/rutas/**", Cupo.GENERAL, true),
            new Ruta(null, "/api/v1/demanda/**", Cupo.GENERAL, true),
            // Desarrollo-135 / HU-134: crear reserva. HU-135: renovarla. HU-124: cancelarla.
            new Ruta(HttpMethod.POST, "/api/v1/reservas", Cupo.GENERAL, true),
            new Ruta(HttpMethod.POST, "/api/v1/reservas/*/renovacion", Cupo.GENERAL, true),
            new Ruta(HttpMethod.DELETE, "/api/v1/reservas/*", Cupo.GENERAL, true),
            // HU-76: estado de la reserva (incluye /mias, bloque B) y "no logre abordar".
            new Ruta(HttpMethod.GET, "/api/v1/reservas/*", Cupo.GENERAL, true),
            new Ruta(HttpMethod.POST, "/api/v1/reservas/*/declaracion-no-abordo", Cupo.GENERAL, true),
            // HU-57: respuesta al aviso de abordaje.
            new Ruta(HttpMethod.POST, "/api/v1/reservas/*/abordaje", Cupo.GENERAL, true),
            new Ruta(HttpMethod.POST, "/api/v1/dispositivos/notificaciones", Cupo.GENERAL, true),
            new Ruta(HttpMethod.POST, "/api/v1/auth/conductor", Cupo.GENERAL, true),
            // SCRUM-173: inicio de sesion del panel municipal.
            new Ruta(HttpMethod.POST, "/api/v1/auth/admin", Cupo.GENERAL, true),
            // SCRUM-26, bloque A: opinion anonima.
            new Ruta(HttpMethod.POST, "/api/v1/opiniones", Cupo.ESTRICTO, true),
            // SCRUM-26, bloque B: abrir sesion y vincular lo hecho en el navegador.
            new Ruta(HttpMethod.POST, "/api/v1/sesion/pasajero", Cupo.ESTRICTO, true),
            new Ruta(HttpMethod.POST, "/api/v1/sesion/pasajero/vincular", Cupo.ESTRICTO, false));

    private RutasPublicas() {
    }

    /**
     * Abre con {@code permitAll()} las rutas anonimas del catalogo. Cada cadena
     * lo llama en su bloque de autorizacion; una ruta que no cae en el
     * {@code securityMatcher} de esa cadena simplemente nunca llega a ella.
     */
    public static void abrir(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rutas) {
        TODAS.stream()
                .filter(Ruta::anonima)
                .forEach(r -> rutas.requestMatchers(r.metodo(), r.patron()).permitAll());
    }
}
