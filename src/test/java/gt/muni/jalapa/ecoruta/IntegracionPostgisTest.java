package gt.muni.jalapa.ecoruta;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integracion. Levanta PostGIS real y deja que Flyway
 * aplique el esquema, que es como corre en produccion.
 *
 * <p>H2 no sirve: no tiene PostGIS, y la mitad del dominio son columnas
 * geometry(Point,4326) (ver ADR-007 y SCRUM-127). Requiere Docker en ejecucion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// El token de admin se declara aqui y no en cada clase para que toda la suite
// comparta un solo contexto de Spring: una propiedad distinta obligaria a
// levantar otro, y arrancar el contexto es lo caro de estas pruebas.
@TestPropertySource(properties = "ecoruta.admin.bootstrap-token=" + IntegracionPostgisTest.ADMIN)
public abstract class IntegracionPostgisTest {

    /** Mecanismo provisional de SCRUM-142 - TODO(SCRUM-134). */
    public static final String ADMIN = "token-de-pruebas-con-mas-de-32-caracteres";

    /**
     * Contenedor SINGLETON: se arranca una sola vez para toda la suite y lo apaga
     * el shutdown hook de Testcontainers.
     *
     * <p>Deliberadamente sin @Container: esa anotacion para el contenedor al
     * terminar cada clase de prueba, pero Spring reutiliza el mismo contexto entre
     * clases y quedaria apuntando a una base apagada.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            // Sin asCompatibleSubstituteFor, PostgreSQLContainer rechaza la imagen
            // por no llamarse "postgres".
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Deja la base como la dejo Flyway. No se tocan las tablas sembradas por las
     * migraciones: las pruebas cuentan con esos datos.
     *
     * <p>El orden importa: las posiciones apuntan a equipos y los equipos a
     * vehiculos, asi que se borra de fuera hacia dentro para no violar las claves
     * foraneas. El BUS-01 que siembra V5 se queda; los vehiculos que cree una
     * prueba se van.
     */
    @BeforeEach
    protected void limpiarDatosDePrueba() {
        jdbc.execute("TRUNCATE posiciones_historicas RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE registros_espera RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE equipos RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM vehiculos WHERE identificador <> 'BUS-01'");
    }
}
