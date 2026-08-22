package gt.muni.jalapa.ecoruta.aceptacion;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Corre los escenarios Gherkin de {@code src/test/resources/features}.
 *
 * <p><b>El nombre termina en Test a proposito.</b> El pipeline ejecuta
 * {@code mvn test} y no {@code mvn verify}, asi que surefire es quien recoge las
 * pruebas, y sus includes son {@code *Test.java} y {@code *IT.java}. Un runner
 * llamado de otra forma se compilaria sin ejecutarse, y el build saldria verde
 * sin haber corrido un solo escenario.
 *
 * <p>El plugin {@code junit:} produce el XML que GitLab publica en el Merge
 * Request: cada escenario aparece como un caso de prueba con su nombre en
 * espanol, legible para QA sin abrir el codigo.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "gt.muni.jalapa.ecoruta.aceptacion")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber/reporte.html, junit:target/cucumber/cucumber.xml")
class PruebasDeAceptacionTest {
}
