package gt.muni.jalapa.ecoruta.identidad.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import gt.muni.jalapa.ecoruta.identidad.FirebaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Arranca el Firebase Admin SDK solo si hay credenciales externas.
 *
 * <p>No se crea un bean {@code FirebaseApp} nulo: un {@code @Bean} que
 * devuelve null deja un NullBean y rompe a quien recolecte por tipo. Quien
 * necesita el app usa {@link ClienteFirebaseAuth}.
 */
@Configuration
@Slf4j
public class FirebaseAdminConfig {

    @Bean
    public ClienteFirebaseAuth clienteFirebaseAuth(FirebaseProperties propiedades) {
        if (!propiedades.tieneCredenciales()) {
            log.warn("Firebase Admin no configurado: POST /api/v1/auth/conductor "
                    + "rechazara todos los idToken hasta definir "
                    + "FIREBASE_CREDENTIALS_PATH o FIREBASE_CREDENTIALS_JSON.");
            return new ClienteFirebaseAdmin(null, propiedades);
        }
        try {
            FirebaseApp app = inicializar(propiedades);
            log.info("Firebase Admin inicializado para el proyecto {}",
                    propiedades.tieneProjectId() ? propiedades.projectId() : app.getOptions().getProjectId());
            return new ClienteFirebaseAdmin(app, propiedades);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudieron leer las credenciales de Firebase Admin", e);
        }
    }

    private static FirebaseApp inicializar(FirebaseProperties propiedades) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credenciales = leerCredenciales(propiedades);
        FirebaseOptions.Builder opciones = FirebaseOptions.builder().setCredentials(credenciales);
        if (propiedades.tieneProjectId()) {
            opciones.setProjectId(propiedades.projectId());
        }
        return FirebaseApp.initializeApp(opciones.build());
    }

    private static GoogleCredentials leerCredenciales(FirebaseProperties propiedades) throws IOException {
        if (StringUtils.hasText(propiedades.credentialsJson())) {
            byte[] json = propiedades.credentialsJson().getBytes(StandardCharsets.UTF_8);
            try (InputStream in = new ByteArrayInputStream(json)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        try (InputStream in = Files.newInputStream(Path.of(propiedades.credentialsPath()))) {
            return GoogleCredentials.fromStream(in);
        }
    }
}
