package gt.muni.jalapa.ecoruta.demanda.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del bus para el conteo del piloto (QA, panel del conductor).
 *
 * <p>{@code capacidad-por-defecto} es el tope de pasajeros a bordo cuando la
 * Municipalidad todavia no cargo la capacidad real del vehiculo
 * ({@code vehiculos.capacidad} en NULL). Por defecto, 25.
 */
@ConfigurationProperties("ecoruta.bus")
public record BusProperties(Integer capacidadPorDefecto) {

    public BusProperties {
        capacidadPorDefecto = (capacidadPorDefecto == null || capacidadPorDefecto <= 0) ? 25 : capacidadPorDefecto;
    }
}
