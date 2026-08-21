package gt.muni.jalapa.ecoruta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puerta de entrada de toda la suite: si el contexto arranca, entonces Flyway
 * aplico las migraciones sobre PostGIS y Hibernate valido que cada @Entity
 * calza con el esquema.
 *
 * <p>Importa porque application.yml fija ddl-auto: validate, asi que cualquier
 * desajuste entre una entidad y su tabla no da un error de prueba: tumba el
 * arranque de la aplicacion. Conviene que ese fallo salga aqui y no en QA.
 */
class EsquemaValidaTest extends IntegracionPostgisTest {

    @Test
    void el_contexto_arranca_con_el_esquema_de_flyway_sobre_postgis() {
        assertThat(jdbc.queryForObject("SELECT postgis_version()", String.class))
                .as("la extension postgis tiene que estar instalada por V1")
                .isNotBlank();
    }

    @Test
    void flyway_aplico_todas_las_migraciones_sin_fallar() {
        Integer pendientes = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertThat(pendientes).isZero();
    }

    @Test
    void las_tablas_del_esquema_inicial_existen() {
        assertThat(tablasPublicas())
                .contains("rutas", "paradas", "registros_espera", "posiciones_historicas", "usuarios");
    }

    @Test
    void la_ubicacion_de_las_posiciones_es_geometry_point_4326() {
        // Si esto cambia, el mapeo JTS de PosicionHistorica deja de calzar.
        assertThat(jdbc.queryForObject("""
                SELECT type FROM geometry_columns
                 WHERE f_table_name = 'posiciones_historicas' AND f_geometry_column = 'ubicacion'
                """, String.class)).isEqualTo("POINT");

        assertThat(jdbc.queryForObject("""
                SELECT srid FROM geometry_columns
                 WHERE f_table_name = 'posiciones_historicas' AND f_geometry_column = 'ubicacion'
                """, Integer.class)).isEqualTo(4326);
    }

    private java.util.List<String> tablasPublicas() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
    }
}
