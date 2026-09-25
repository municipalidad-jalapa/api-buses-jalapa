package gt.muni.jalapa.ecoruta.aceptacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de {@code metricas_del_panel_municipal.feature} (SCRUM-26, bloque F). */
public class PasosDeMetricasDelPanel {

    private static final String CABECERA_ADMIN = "X-Admin-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContextoDelEscenario contexto;

    @Autowired
    private ObjectMapper json;

    private final AtomicInteger secuencia = new AtomicInteger();
    private JsonNode abordajes;
    private JsonNode opiniones;

    @Before("@bloque-F")
    public void limpiar() {
        jdbc.update("DELETE FROM opiniones");
    }

    // --- criterio 1 ----------------------------------------------------------

    @Dado("que el piloto marcó {int} abordajes en la ruta {long}")
    @Dado("que el piloto marcó {int} abordaje en la ruta {long}")
    public void el_piloto_marco(int cuantos, long rutaId) {
        for (int i = 0; i < cuantos; i++) {
            jdbc.update("""
                    INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en,
                                                  subio, abordaje_fuente, abordaje_en, abordado_por)
                    SELECT ?, p.id, 'ABORDO', now(), now() + interval '5 minutes',
                           TRUE, 'CONDUCTOR', now(), 'conductor1'
                      FROM paradas p WHERE p.ruta_id = ? ORDER BY p.orden LIMIT 1
                    """, "nav-bdd-f-" + secuencia.incrementAndGet(), rutaId);
        }
    }

    @Y("que un pasajero dijo haber abordado sin que el piloto lo marcara")
    public void el_pasajero_dijo_que_subio() {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en,
                                              subio, abordaje_fuente, abordaje_en)
                SELECT ?, p.id, 'ABORDO', now(), now() + interval '5 minutes',
                       TRUE, 'PASAJERO', now()
                  FROM paradas p WHERE p.ruta_id = 1 ORDER BY p.orden LIMIT 1
                """, "nav-bdd-f-pas-" + secuencia.incrementAndGet());
    }

    @Cuando("consulto los abordajes del panel")
    public void consulto_los_abordajes() throws Exception {
        abordajes = json.readTree(mockMvc.perform(get("/api/v1/admin/abordajes")
                        .header(CABECERA_ADMIN, IntegracionPostgisTest.ADMIN))
                .andReturn().getResponse().getContentAsString());
    }

    @Entonces("el total de pasajeros subidos es {int}")
    public void el_total_de_subidos(int total) {
        assertThat(abordajes.get("total").asInt()).isEqualTo(total);
    }

    @Y("el conteo viene desglosado por ruta, por vehículo y por periodo")
    public void el_conteo_viene_desglosado() {
        assertThat(abordajes.get("porRuta").size()).isEqualTo(2);
        assertThat(abordajes.get("porVehiculo").isArray()).isTrue();
        assertThat(abordajes.get("porPeriodo").size()).isPositive();
    }

    // --- criterios 2 y 3 -----------------------------------------------------

    @Cuando("opino la ruta {long} con calidad {int}, limpieza {int} y conducción {int}")
    @Dado("que opiné la ruta {long} con calidad {int}, limpieza {int} y conducción {int}")
    public void opino_con_dimensiones(long rutaId, int calidad, int limpieza, int conduccion) throws Exception {
        StringBuilder cuerpo = new StringBuilder(
                "{\"tipo\":\"calificacion\",\"rutaId\":%d".formatted(rutaId));
        // 0 en el escenario significa "no la puntuó": el campo no se manda.
        if (calidad > 0) {
            cuerpo.append(",\"calidad\":").append(calidad);
        }
        if (limpieza > 0) {
            cuerpo.append(",\"limpieza\":").append(limpieza);
        }
        if (conduccion > 0) {
            cuerpo.append(",\"conduccion\":").append(conduccion);
        }
        enviar(cuerpo.append('}').toString());
    }

    @Cuando("opino la ruta {long} solo con la calificación general")
    public void opino_solo_general(long rutaId) throws Exception {
        enviar("{\"tipo\":\"calificacion\",\"rutaId\":%d,\"estrellas\":5}".formatted(rutaId));
    }

    @Cuando("consulto las opiniones del panel")
    public void consulto_las_opiniones() throws Exception {
        opiniones = json.readTree(mockMvc.perform(get("/api/v1/opiniones")
                        .header(CABECERA_ADMIN, IntegracionPostgisTest.ADMIN))
                .andReturn().getResponse().getContentAsString());
    }

    @Entonces("el promedio de {word} de la ruta {long} es {word}")
    public void el_promedio_es(String dimension, long rutaId, String esperado) {
        assertThat(promedio(dimension, rutaId).asDouble())
                .isEqualTo(Double.parseDouble(esperado));
    }

    @Entonces("el promedio de {word} de la ruta {long} no tiene dato")
    public void el_promedio_no_tiene_dato(String dimension, long rutaId) {
        assertThat(promedio(dimension, rutaId).isNull()).isTrue();
    }

    private JsonNode promedio(String dimension, long rutaId) {
        String campo = switch (dimension) {
            case "calidad" -> "calidad";
            case "limpieza" -> "limpieza";
            case "conducción" -> "conduccion";
            default -> throw new IllegalArgumentException("Dimension desconocida: " + dimension);
        };
        for (JsonNode fila : opiniones.get("resumen").get("promedioPorRuta")) {
            if (fila.get("id").asLong() == rutaId) {
                return fila.path(campo);
            }
        }
        throw new AssertionError("La ruta " + rutaId + " no esta en el resumen");
    }

    private void enviar(String cuerpo) throws Exception {
        contexto.guardarRespuesta(mockMvc.perform(post("/api/v1/opiniones")
                .header("X-Dispositivo-Id", "nav-bdd-f-" + secuencia.incrementAndGet())
                .contentType(APPLICATION_JSON)
                .content(cuerpo)));
    }
}
