package gt.muni.jalapa.ecoruta.catalogo;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-136 (HU-41): datos reales de las rutas de Jalapa.
 *
 * <p>Contrasta geometrias crudas en PostGIS (no solo DTOs) para que una inversion
 * simultanea al escribir y leer no produzca un falso positivo. Orden espacial:
 * {@code ST_X = longitud}, {@code ST_Y = latitud}.
 */
class DatosRealesRutasIT extends IntegracionPostgisTest {

    /** Tolerancia decimal al comparar coordenadas de campo (grados). */
    private static final double TOL_COORD = 1e-6;

    /**
     * Distancia maxima (metros, geography) entre una parada y su trazado.
     * El levantamiento de paradas y el track GPS no son el mismo muestreo.
     */
    private static final double TOL_PARADA_SOBRE_TRAZADO_M = 120.0;

    private static final double[][] PRINCIPAL = {
            {14.634922, -89.981133},
            {14.632649, -89.986805},
            {14.630572, -89.992877},
            {14.629705, -89.996203},
            {14.627899, -90.003096},
            {14.629016, -89.996073},
            {14.629725, -89.992588},
            {14.631173, -89.987091},
    };

    private static final double[][] SECUNDARIA = {
            {14.634922, -89.981133},
            {14.632317, -89.988729},
            {14.634647, -89.989157},
            {14.639865, -89.991095},
            {14.651840, -89.999791},
            {14.658814, -90.000046},
    };

    @Test
    void existen_exactamente_las_dos_rutas_activas_sembradas() {
        List<String> nombres = jdbc.queryForList(
                "SELECT nombre FROM rutas WHERE activa ORDER BY id", String.class);
        assertThat(nombres).containsExactly("RUTA PRINCIPAL", "RUTA SECUNDARIA");
    }

    @Test
    void la_principal_tiene_ocho_paradas_ordenadas_sin_novena_duplicada() {
        Long rutaId = idDeRuta("RUTA PRINCIPAL");
        List<Map<String, Object>> paradas = paradasDe(rutaId);

        assertThat(paradas).hasSize(8);
        for (int i = 0; i < 8; i++) {
            assertThat(paradas.get(i).get("orden")).isEqualTo(i + 1);
            assertThat(paradas.get(i).get("nombre")).isEqualTo("Parada " + (i + 1));
        }
        Integer novenas = jdbc.queryForObject(
                "SELECT count(*) FROM paradas WHERE ruta_id = ? AND (orden = 9 OR nombre = 'Parada 9')",
                Integer.class, rutaId);
        assertThat(novenas).isZero();
    }

    @Test
    void la_secundaria_tiene_seis_paradas_ordenadas_del_tramo_parcial() {
        Long rutaId = idDeRuta("RUTA SECUNDARIA");
        List<Map<String, Object>> paradas = paradasDe(rutaId);

        assertThat(paradas).hasSize(6);
        for (int i = 0; i < 6; i++) {
            assertThat(paradas.get(i).get("orden")).isEqualTo(i + 1);
            assertThat(paradas.get(i).get("nombre")).isEqualTo("Parada " + (i + 1));
        }
    }

    @Test
    void las_coordenadas_de_cada_parada_coinciden_con_el_levantamiento() {
        assertParadasEnCampo("RUTA PRINCIPAL", PRINCIPAL);
        assertParadasEnCampo("RUTA SECUNDARIA", SECUNDARIA);
    }

    @Test
    void todas_las_geometrias_de_parada_son_point_srid_4326() {
        Integer malas = jdbc.queryForObject("""
                SELECT count(*) FROM paradas
                 WHERE GeometryType(ubicacion) <> 'POINT'
                    OR ST_SRID(ubicacion) <> 4326
                """, Integer.class);
        assertThat(malas).isZero();
    }

    @Test
    void ambos_trazados_son_linestring_srid_4326() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT nombre,
                       GeometryType(trazado) AS tipo,
                       ST_SRID(trazado) AS srid
                  FROM rutas
                 WHERE nombre IN ('RUTA PRINCIPAL', 'RUTA SECUNDARIA')
                """);
        assertThat(filas).hasSize(2);
        for (Map<String, Object> fila : filas) {
            assertThat(fila.get("tipo")).isEqualTo("LINESTRING");
            assertThat(fila.get("srid")).isEqualTo(4326);
        }
    }

    @Test
    void la_principal_es_circuito_cerrado_que_empieza_y_termina_en_el_punto_a() {
        Long rutaId = idDeRuta("RUTA PRINCIPAL");
        Boolean cerrado = jdbc.queryForObject(
                "SELECT ST_IsClosed(trazado) FROM rutas WHERE id = ?", Boolean.class, rutaId);
        assertThat(cerrado).isTrue();

        Map<String, Object> extremos = jdbc.queryForMap("""
                SELECT ST_X(ST_StartPoint(trazado)) AS lon_ini,
                       ST_Y(ST_StartPoint(trazado)) AS lat_ini,
                       ST_X(ST_EndPoint(trazado))   AS lon_fin,
                       ST_Y(ST_EndPoint(trazado))   AS lat_fin
                  FROM rutas WHERE id = ?
                """, rutaId);

        assertThat((Double) extremos.get("lon_ini")).isCloseTo(-89.981133, within(TOL_COORD));
        assertThat((Double) extremos.get("lat_ini")).isCloseTo(14.634922, within(TOL_COORD));
        assertThat((Double) extremos.get("lon_fin")).isCloseTo(-89.981133, within(TOL_COORD));
        assertThat((Double) extremos.get("lat_fin")).isCloseTo(14.634922, within(TOL_COORD));
    }

    @Test
    void la_principal_tiene_mas_vertices_que_paradas_y_longitud_razonable() {
        Long rutaId = idDeRuta("RUTA PRINCIPAL");
        Integer vertices = jdbc.queryForObject(
                "SELECT ST_NPoints(trazado) FROM rutas WHERE id = ?", Integer.class, rutaId);
        Integer paradas = jdbc.queryForObject(
                "SELECT count(*) FROM paradas WHERE ruta_id = ?", Integer.class, rutaId);
        assertThat(vertices).isGreaterThan(paradas);

        // Circuito levantado ~5.1 km; rango tolerante 4.8–5.6 km sobre geography.
        Double km = jdbc.queryForObject("""
                SELECT ST_Length(trazado::geography) / 1000.0 FROM rutas WHERE id = ?
                """, Double.class, rutaId);
        assertThat(km).isBetween(4.8, 5.6);
    }

    @Test
    void la_secundaria_es_parcial_abierta_con_mas_vertices_que_paradas() {
        // Tramo parcial: no se cierra artificialmente ni se inventa el regreso.
        Long rutaId = idDeRuta("RUTA SECUNDARIA");
        Boolean cerrado = jdbc.queryForObject(
                "SELECT ST_IsClosed(trazado) FROM rutas WHERE id = ?", Boolean.class, rutaId);
        assertThat(cerrado).as("RUTA SECUNDARIA debe permanecer abierta (parcial)").isFalse();

        Integer vertices = jdbc.queryForObject(
                "SELECT ST_NPoints(trazado) FROM rutas WHERE id = ?", Integer.class, rutaId);
        Integer paradas = jdbc.queryForObject(
                "SELECT count(*) FROM paradas WHERE ruta_id = ?", Integer.class, rutaId);
        assertThat(vertices).isGreaterThan(paradas);
    }

    @Test
    void ninguna_coordenada_esta_invertida_respecto_a_jalapa() {
        // Latitudes ~14–15 (Jalapa); longitudes negativas ~-91 a -89 (Guatemala).
        Integer invertidas = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon FROM paradas
                    UNION ALL
                    SELECT ST_Y(geom), ST_X(geom)
                      FROM rutas, LATERAL ST_DumpPoints(trazado) AS dp(path, geom)
                ) pts
                 WHERE lat NOT BETWEEN 14.0 AND 15.0
                    OR lon NOT BETWEEN -91.0 AND -89.0
                """, Integer.class);
        assertThat(invertidas).isZero();
    }

    @Test
    void las_paradas_quedan_sobre_o_cerca_de_su_trazado() {
        Integer lejos = jdbc.queryForObject("""
                SELECT count(*)
                  FROM paradas p
                  JOIN rutas r ON r.id = p.ruta_id
                 WHERE NOT ST_DWithin(
                         p.ubicacion::geography,
                         r.trazado::geography,
                         ?)
                """, Integer.class, TOL_PARADA_SOBRE_TRAZADO_M);
        assertThat(lejos)
                .as("todas las paradas deben estar a <= %s m del trazado (geography)",
                        TOL_PARADA_SOBRE_TRAZADO_M)
                .isZero();
    }

    @Test
    void cada_ruta_tiene_su_bus_y_exactamente_un_vehiculo_activo() {
        Map<String, Object> bus1 = jdbc.queryForMap("""
                SELECT v.identificador, v.placa, r.nombre AS ruta
                  FROM vehiculos v
                  JOIN rutas r ON r.id = v.ruta_id
                 WHERE v.identificador = 'BUS-1'
                """);
        assertThat(bus1.get("placa")).isEqualTo("MIBUS-001");
        assertThat(bus1.get("ruta")).isEqualTo("RUTA PRINCIPAL");

        Map<String, Object> bus2 = jdbc.queryForMap("""
                SELECT v.identificador, v.placa, r.nombre AS ruta
                  FROM vehiculos v
                  JOIN rutas r ON r.id = v.ruta_id
                 WHERE v.identificador = 'BUS-2'
                """);
        assertThat(bus2.get("placa")).isEqualTo("MIBUS-002");
        assertThat(bus2.get("ruta")).isEqualTo("RUTA SECUNDARIA");

        List<Map<String, Object>> porRuta = jdbc.queryForList("""
                SELECT r.nombre, count(v.id) AS buses
                  FROM rutas r
                  JOIN vehiculos v ON v.ruta_id = r.id AND v.activo
                 WHERE r.activa
                 GROUP BY r.nombre
                """);
        assertThat(porRuta).hasSize(2);
        for (Map<String, Object> fila : porRuta) {
            assertThat(((Number) fila.get("buses")).intValue()).isEqualTo(1);
        }
    }

    @Test
    void get_rutas_devuelve_ambas_con_el_mismo_contrato() throws Exception {
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.nombre=='RUTA PRINCIPAL')].paradas.length()")
                        .value(org.hamcrest.Matchers.contains(8)))
                .andExpect(jsonPath("$[?(@.nombre=='RUTA SECUNDARIA')].paradas.length()")
                        .value(org.hamcrest.Matchers.contains(6)))
                .andExpect(jsonPath("$[?(@.nombre=='RUTA PRINCIPAL')].trazado").exists())
                .andExpect(jsonPath("$[?(@.nombre=='RUTA SECUNDARIA')].trazado").exists());
    }

    private void assertParadasEnCampo(String nombreRuta, double[][] esperadas) {
        Long rutaId = idDeRuta(nombreRuta);
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT orden, ST_Y(ubicacion) AS lat, ST_X(ubicacion) AS lon
                  FROM paradas
                 WHERE ruta_id = ?
                 ORDER BY orden
                """, rutaId);
        assertThat(filas).hasSize(esperadas.length);
        for (int i = 0; i < esperadas.length; i++) {
            assertThat((Double) filas.get(i).get("lat"))
                    .as("%s orden %s lat", nombreRuta, i + 1)
                    .isCloseTo(esperadas[i][0], within(TOL_COORD));
            assertThat((Double) filas.get(i).get("lon"))
                    .as("%s orden %s lon (ST_X)", nombreRuta, i + 1)
                    .isCloseTo(esperadas[i][1], within(TOL_COORD));
        }
    }

    private Long idDeRuta(String nombre) {
        return jdbc.queryForObject("SELECT id FROM rutas WHERE nombre = ?", Long.class, nombre);
    }

    private List<Map<String, Object>> paradasDe(Long rutaId) {
        return jdbc.queryForList("""
                SELECT orden, nombre FROM paradas WHERE ruta_id = ? ORDER BY orden
                """, rutaId);
    }
}
