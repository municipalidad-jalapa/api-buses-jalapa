package gt.muni.jalapa.ecoruta.identidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Credenciales del Firebase Admin SDK. Todo llega por variable de entorno;
 * nada de esto se commitea.
 *
 * @param credentialsPath ruta a un JSON de cuenta de servicio
 * @param credentialsJson JSON de la cuenta de servicio (util en contenedores)
 * @param projectId       si esta definido, el idToken debe pertenecer a este proyecto
 */
@ConfigurationProperties("ecoruta.firebase")
public record FirebaseProperties(String credentialsPath, String credentialsJson, String projectId) {

    public boolean tieneCredenciales() {
        return StringUtils.hasText(credentialsPath) || StringUtils.hasText(credentialsJson);
    }

    public boolean tieneProjectId() {
        return StringUtils.hasText(projectId);
    }
}
