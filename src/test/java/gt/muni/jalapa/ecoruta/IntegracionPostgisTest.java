package gt.muni.jalapa.ecoruta;

import gt.muni.jalapa.ecoruta.eta.servicio.EtaService;
import gt.muni.jalapa.ecoruta.seguridad.ratelimit.RateLimitFilter;
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
@TestPropertySource(properties = {
        "ecoruta.admin.bootstrap-token=" + IntegracionPostgisTest.ADMIN,
        "ecoruta.integraciones.traccar.token=" + IntegracionPostgisTest.TRACCAR})
public abstract class IntegracionPostgisTest {

    /** Mecanismo provisional de SCRUM-142 - TODO(SCRUM-134). */
    public static final String ADMIN = "token-de-pruebas-con-mas-de-32-caracteres";

    /** Secreto de la integracion con Traccar en las pruebas (SCRUM-24). */
    public static final String TRACCAR = "traccar-de-pruebas-con-mas-de-32-caracteres";

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
            // SCRUM-26, bloque C: la imagen trae ademas pgRouting, que usa el
            // enrutado del desvio por calles.
            DockerImageName.parse("pgrouting/pgrouting:17-3.5-3.8")
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
     * foraneas. Los buses sembrados se quedan (BUS-01 de V5 y BUS-02 de V12, uno
     * por ruta); los vehiculos que cree una prueba se van.
     */
    @BeforeEach
    protected void limpiarDatosDePrueba() {
        jdbc.execute("TRUNCATE fallos_de_aviso RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE dispositivos_notificacion");
        jdbc.execute("TRUNCATE posiciones_historicas RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE registros_espera RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE equipos RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM vehiculos WHERE identificador NOT IN ('BUS-01', 'BUS-02')");
        // El ETA vive en memoria y el contexto se comparte entre clases (SCRUM-166).
        etas.olvidarTodo();
        // Los limites tambien: el cupo de una clase no debe gastarse en otra.
        limites.reiniciar();
    }

    @Autowired
    private EtaService etas;

    @Autowired
    private RateLimitFilter limites;
}
