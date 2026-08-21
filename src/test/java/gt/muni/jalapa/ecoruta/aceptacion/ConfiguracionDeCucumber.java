package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Enlaza Cucumber con el contexto de Spring.
 *
 * <p>Hereda de {@link IntegracionPostgisTest} para reutilizar exactamente la
 * misma configuracion: el mismo contenedor PostGIS singleton y las mismas
 * anotaciones. Si declarara las suyas propias, Spring veria otra clave de cache y
 * levantaria un SEGUNDO contexto -- con su segundo contenedor -- solo para los
 * escenarios.
 *
 * <p>{@code @CucumberContextConfiguration} debe estar en UNA sola clase del glue.
 */
@CucumberContextConfiguration
public class ConfiguracionDeCucumber extends IntegracionPostgisTest {

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private RegistroDelEscenario registro;

    /**
     * Cucumber no ejecuta el ciclo de vida de JUnit, asi que el {@code @BeforeEach}
     * de la clase base nunca correria y los escenarios arrastrarian datos entre si.
     *
     * <p>Ojo con el import: este {@code @Before} es el de {@code io.cucumber.java},
     * no el de JUnit.
     */
    @Before
    public void prepararEscenario() {
        limpiarDatosDePrueba();
        contexto.reiniciar();
        registro.empezarACapturar();
    }

    @After
    public void cerrarEscenario() {
        registro.dejarDeCapturar();
    }
}
