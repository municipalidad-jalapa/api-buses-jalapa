package gt.muni.jalapa.ecoruta.catalogo;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-130 (HU-35): consultar rutas y paradas. */
class ConsultarRutasIT extends IntegracionPostgisTest {

    @Test
    void devuelve_las_rutas_activas_con_sus_paradas() throws Exception {
        // V6 siembra la ruta de ejemplo: un circuito con ocho paradas.
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Ruta de ejemplo - Centro de Jalapa"))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[0].paradas", org.hamcrest.Matchers.hasSize(8)));
    }

    @Test
    void es_publico_y_no_pide_credencial() throws Exception {
        // El pasajero es anonimo: no hay login en el producto.
        mockMvc.perform(get("/api/v1/rutas")).andExpect(status().isOk());
    }

    @Test
    void las_paradas_vienen_en_el_orden_del_recorrido() throws Exception {
        // El mapa dibuja la linea siguiendo esta secuencia: si el orden se
        // pierde, la ruta sale hecha un garabato.
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(jsonPath("$[0].paradas[0].orden").value(1))
                .andExpect(jsonPath("$[0].paradas[0].nombre").value("Parque Central"))
                .andExpect(jsonPath("$[0].paradas[7].orden").value(8))
                .andExpect(jsonPath("$[0].paradas[7].nombre").value("Transito Rojas - Chipilapa"));
    }

    @Test
    void las_coordenadas_salen_con_nombre_y_sin_invertirse() throws Exception {
        // ADR-007, el error clasico: PostGIS guarda (lon, lat). Se comprueba
        // contra el valor crudo de la base, no solo contra el DTO, porque a
        // nivel de DTO pasaria igual si escritura y lectura invirtieran a la vez.
        Double latEnBase = jdbc.queryForObject(
                "SELECT ST_Y(ubicacion) FROM paradas WHERE orden = 1", Double.class);
        Double lonEnBase = jdbc.queryForObject(
                "SELECT ST_X(ubicacion) FROM paradas WHERE orden = 1", Double.class);

        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(jsonPath("$[0].paradas[0].latitud").value(latEnBase))
                .andExpect(jsonPath("$[0].paradas[0].longitud").value(lonEnBase));

        // Jalapa esta al norte del ecuador y al oeste de Greenwich.
        assertThat(latEnBase).isBetween(14.0, 15.0);
        assertThat(lonEnBase).isBetween(-91.0, -89.0);
    }

    @Test
    void una_ruta_inactiva_no_aparece_en_el_listado() throws Exception {
        jdbc.update("UPDATE rutas SET activa = false WHERE id = 1");
        try {
            mockMvc.perform(get("/api/v1/rutas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        } finally {
            jdbc.update("UPDATE rutas SET activa = true WHERE id = 1");
        }
    }

    @Test
    void se_puede_consultar_una_ruta_por_su_identificador() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.paradas", org.hamcrest.Matchers.hasSize(8)));
    }

    @Test
    void una_ruta_que_no_existe_responde_404_con_el_formato_ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/rutas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/rutas/999999"));
    }
}
