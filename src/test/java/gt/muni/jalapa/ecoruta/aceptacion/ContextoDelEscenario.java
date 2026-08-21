package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado compartido entre los pasos de un mismo escenario.
 *
 * <p>Se limpia en el {@code @Before} de {@link ConfiguracionDeCucumber} en vez de
 * usar {@code @ScenarioScope}: el contexto de Spring es el mismo que usan las
 * pruebas JUnit, y un ambito propio de Cucumber sobre un contexto compartido es
 * una fuente de sorpresas que no compensa para un puñado de campos.
 */
@Component
public class ContextoDelEscenario {

    private final List<AltaDeEquipo> equipos = new ArrayList<>();
    private ResultActions ultimaRespuesta;

    /** Credencial de un equipo emitido por la API, no por el servicio (SCRUM-143). */
    private String credencialCruda;

    /** Conexiones SSE abiertas en el escenario (SCRUM-140). */
    private final List<MvcResult> streams = new ArrayList<>();

    public void reiniciar() {
        equipos.clear();
        ultimaRespuesta = null;
        credencialCruda = null;
        streams.clear();
    }

    public void registrarStream(MvcResult stream) {
        streams.add(stream);
    }

    public MvcResult stream(int indice) {
        return streams.get(indice);
    }

    public void guardarCredencialCruda(String credencial) {
        this.credencialCruda = credencial;
    }

    public String credencialCruda() {
        return credencialCruda;
    }

    public void registrarEquipo(AltaDeEquipo alta) {
        equipos.add(alta);
    }

    /** El equipo del que habla el escenario cuando solo hay uno. */
    public AltaDeEquipo equipo() {
        return equipos.get(0);
    }

    public AltaDeEquipo equipo(int indice) {
        return equipos.get(indice);
    }

    public int cuantosEquipos() {
        return equipos.size();
    }

    public ResultActions ultimaRespuesta() {
        return ultimaRespuesta;
    }

    public void guardarRespuesta(ResultActions respuesta) {
        this.ultimaRespuesta = respuesta;
    }
}
