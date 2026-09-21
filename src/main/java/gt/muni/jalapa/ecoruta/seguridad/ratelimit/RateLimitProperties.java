package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de peticiones sobre los endpoints publicos (HU Desarrollo-95).
 *
 * <p>Dos limites independientes, cada uno con su propia capacidad y ventana:
 * uno por IP y uno por dispositivo ({@code X-Dispositivo-Id}). Una peticion se
 * rechaza con 429 si cualquiera de los dos se agota. El limite por dispositivo
 * solo aplica cuando la cabecera viene presente; las rutas publicas que no la
 * usan (catalogo de rutas, posicion vigente, stream) solo quedan cubiertas por
 * el limite de IP.
 *
 * <p>Ventana fija en memoria: correcto para el despliegue de este proyecto, un
 * unico contenedor sin replicas (ADR-009). Un limitador distribuido con Redis
 * seria sobre-ingenieria para una ruta de bus en un municipio.
 *
 * @param habilitado                si el filtro de limite de peticiones esta activo
 * @param porIpCapacidad             maximo de peticiones por IP dentro de la ventana
 * @param porIpVentanaSegundos       duracion de la ventana del limite por IP
 * @param porDispositivoCapacidad    maximo de peticiones por dispositivo dentro de la ventana
 * @param porDispositivoVentanaSegundos duracion de la ventana del limite por dispositivo
 */
@ConfigurationProperties("ecoruta.rate-limit")
public record RateLimitProperties(
        boolean habilitado,
        int porIpCapacidad,
        int porIpVentanaSegundos,
        int porDispositivoCapacidad,
        int porDispositivoVentanaSegundos) {

    public RateLimitProperties {
        porIpCapacidad = porIpCapacidad <= 0 ? 300 : porIpCapacidad;
        porIpVentanaSegundos = porIpVentanaSegundos <= 0 ? 60 : porIpVentanaSegundos;
        porDispositivoCapacidad = porDispositivoCapacidad <= 0 ? 60 : porDispositivoCapacidad;
        porDispositivoVentanaSegundos = porDispositivoVentanaSegundos <= 0 ? 60 : porDispositivoVentanaSegundos;
    }
}
