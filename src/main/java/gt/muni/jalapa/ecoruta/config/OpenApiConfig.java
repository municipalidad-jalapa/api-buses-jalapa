package gt.muni.jalapa.ecoruta.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "credencialEquipo",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = """
                Credencial propia del equipo a bordo (SCRUM-142).
                Formato: eq_<codigoPublico>.<secreto>.

                Se emite con POST /api/v1/admin/equipos y se muestra UNA sola vez.
                Viaja siempre en la cabecera Authorization, nunca en la URL.""")
public class OpenApiConfig {

    @Bean
    public OpenAPI ecoRutaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("EcoRuta API")
                .description("Plataforma del bus el\u00e9ctrico municipal de Jalapa")
                .version("v1"));
    }
}
