package gt.muni.jalapa.ecoruta.telemetria.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param ventanaHoras      tolerancia contra relojes desfasados del equipo a
 *                          bordo (SCRUM-138). Simetrica: la fija asi el criterio
 * @param sseTimeoutMinutos cuanto vive una conexion del stream antes de que el
 *                          servidor la cierre. Al expirar, EventSource reconecta
 *                          solo (ADR-008), asi que no es una perdida de servicio
 * @param latidoSegundos    cada cuanto se manda un comentario SSE para que el
 *                          proxy no de por muerta una conexion inactiva. Por
 *                          debajo de los 60 s de proxy_read_timeout de Nginx
 * @param reconexionSegundos lo que se envia en el campo retry: de cada evento
 */
@ConfigurationProperties("ecoruta.telemetria")
public record TelemetriaProperties(int ventanaHoras, int sseTimeoutMinutos,
                                   int latidoSegundos, int reconexionSegundos) {

    public TelemetriaProperties {
        ventanaHoras = ventanaHoras <= 0 ? 12 : ventanaHoras;
        sseTimeoutMinutos = sseTimeoutMinutos <= 0 ? 30 : sseTimeoutMinutos;
        latidoSegundos = latidoSegundos <= 0 ? 25 : latidoSegundos;
        reconexionSegundos = reconexionSegundos <= 0 ? 3 : reconexionSegundos;
    }

    public Duration ventana() {
        return Duration.ofHours(ventanaHoras);
    }

    public Duration sseTimeout() {
        return Duration.ofMinutes(sseTimeoutMinutos);
    }

    public Duration latido() {
        return Duration.ofSeconds(latidoSegundos);
    }

    public Duration reconexion() {
        return Duration.ofSeconds(reconexionSegundos);
    }
}
