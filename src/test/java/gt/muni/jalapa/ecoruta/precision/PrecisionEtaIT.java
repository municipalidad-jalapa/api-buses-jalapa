package gt.muni.jalapa.ecoruta.precision;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import gt.muni.jalapa.ecoruta.precision.repositorio.PrediccionEtaRepository;
import gt.muni.jalapa.ecoruta.precision.servicio.DetectorDeLlegadaReal;
import gt.muni.jalapa.ecoruta.precision.servicio.GeneradorDePrediccionesSimuladas;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-73: persistencia, asociacion a la prediccion vigente y consulta agrupada
 * sin mezclar rutas.
 */
class PrecisionEtaIT extends IntegracionPostgisTest {

    private static final Instant PREDICHO = Instant.parse("2026-09-13T12:00:00Z");
    private static final Instant LLEGADA = Instant.parse("2026-09-13T12:10:00Z");

    @Autowired
    private PrediccionEtaRepository predicciones;
    @Autowired
    private DetectorDeLlegadaReal detector;

    private GeneradorDePrediccionesSimuladas generador;

    @BeforeEach
    void limpiarPrecision() {
        jdbc.execute("TRUNCATE llegadas_reales RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE predicciones_eta RESTART IDENTITY CASCADE");
        jdbc.execute("DELETE FROM paradas WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre = 'Ruta B HU-73')");
        jdbc.execute("DELETE FROM rutas WHERE nombre = 'Ruta B HU-73'");
        generador = new GeneradorDePrediccionesSimuladas(predicciones);
    }

    @AfterEach
    void quitarRutaDeAislamiento() {
        jdbc.execute("TRUNCATE llegadas_reales RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE predicciones_eta RESTART IDENTITY CASCADE");
        jdbc.execute("DELETE FROM paradas WHERE ruta_id IN (SELECT id FROM rutas WHERE nombre = 'Ruta B HU-73')");
        jdbc.execute("DELETE FROM rutas WHERE nombre = 'Ruta B HU-73'");
    }

    @Test
    void persiste_la_prediccion_simulada_con_ruta_parada_vehiculo_y_marca_de_tiempo() {
        PrediccionEta guardada = generador.simular(1L, 1L, 1L, 8, PREDICHO);

        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getRutaId()).isEqualTo(1L);
        assertThat(guardada.getParadaId()).isEqualTo(1L);
        assertThat(guardada.getVehiculoId()).isEqualTo(1L);
        assertThat(guardada.getEtaPredichoMin()).isEqualTo(8);
        assertThat(guardada.getPredichoEn()).isEqualTo(PREDICHO);
        assertThat(guardada.isSimulada()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM predicciones_eta", Integer.class)).isEqualTo(1);
    }

    @Test
    void asocia_la_llegada_detectada_a_la_prediccion_vigente() {
        generador.simular(1L, 1L, 1L, 8, PREDICHO);

        var llegada = detector.detectar(new PosicionActualResponse(
                14.634878, -89.981202, 0.0, LLEGADA, "BUS-01"));

        assertThat(llegada).isPresent();
        assertThat(llegada.get().getErrorMin()).isEqualTo(2.0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM llegadas_reales", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT prediccion_id FROM llegadas_reales", Long.class)).isPositive();
    }

    @Test
    void la_consulta_agrega_sin_mezclar_rutas() throws Exception {
        Long ruta2 = jdbc.queryForObject(
                "INSERT INTO rutas (nombre, activa) VALUES ('Ruta B HU-73', false) RETURNING id",
                Long.class);
        Long parada2 = jdbc.queryForObject("""
                INSERT INTO paradas (nombre, ubicacion, orden, ruta_id)
                VALUES ('Otra', ST_SetSRID(ST_MakePoint(-89.99, 14.64), 4326), 1, ?)
                RETURNING id
                """, Long.class, ruta2);

        generador.simular(1L, 1L, 1L, 8, PREDICHO);
        detector.detectar(new PosicionActualResponse(14.634878, -89.981202, 0.0, LLEGADA, "BUS-01"));

        PrediccionEta deOtra = generador.simular(ruta2, parada2, 1L, 5, PREDICHO);
        jdbc.update("""
                INSERT INTO llegadas_reales (prediccion_id, llegada_en, error_min)
                VALUES (?, TIMESTAMPTZ '2026-09-13T12:10:00Z', 40.0)
                """, deOtra.getId());

        mockMvc.perform(get("/api/v1/rutas/1/eta/precision")
                        .param("desde", "2026-09-13T00:00:00Z")
                        .param("hasta", "2026-09-13T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(1))
                .andExpect(jsonPath("$.errorPromedioMin").value(2.0))
                .andExpect(jsonPath("$.errorMaximoMin").value(2.0))
                .andExpect(jsonPath("$.porParada.length()").value(1))
                .andExpect(jsonPath("$.porParada[0].paradaId").value(1))
                .andExpect(jsonPath("$.porParada[0].muestras").value(1))
                .andExpect(jsonPath("$.porFranja.length()").value(1));

        mockMvc.perform(get("/api/v1/rutas/" + ruta2 + "/eta/precision")
                        .param("desde", "2026-09-13T00:00:00Z")
                        .param("hasta", "2026-09-13T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rutaId").value(ruta2.intValue()))
                .andExpect(jsonPath("$.errorPromedioMin").value(40.0))
                .andExpect(jsonPath("$.porParada[0].paradaId").value(parada2.intValue()));
    }
}
