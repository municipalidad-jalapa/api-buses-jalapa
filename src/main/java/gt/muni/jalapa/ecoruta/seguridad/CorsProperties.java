package gt.muni.jalapa.ecoruta.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origenes permitidos para la aplicacion web.
 *
 * <p>Vacio por defecto: hoy no hay ningun origen habilitado. El gancho existe
 * para que SCRUM-274 (HU-122) solo tenga que llenar los origenes por ambiente y
 * escribir sus pruebas de preflight, sin reescribir SecurityConfig.
 *
 * @param origenesPermitidos lista de origenes exactos, ej. https://qa.buses.jalapa.gob.gt
 */
@ConfigurationProperties("ecoruta.cors")
public record CorsProperties(List<String> origenesPermitidos) {

    public CorsProperties {
        origenesPermitidos = origenesPermitidos == null ? List.of() : List.copyOf(origenesPermitidos);
    }
}
