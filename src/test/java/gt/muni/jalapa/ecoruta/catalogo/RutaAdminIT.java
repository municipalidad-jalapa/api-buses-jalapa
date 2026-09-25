package gt.muni.jalapa.ecoruta.catalogo;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA 5.6: corregir la ruta desde el panel municipal. Antes no habia pantalla
 * ni endpoint: el acceso terminaba en "Pagina no encontrada".
 */
class RutaAdminIT extends IntegracionPostgisTest {

    private String trazadoOriginal;
    private Map<String, Object> paradaOriginal;

    @BeforeEach
    void guardarOriginales() {
        trazadoOriginal = jdbc.queryForObject("SELECT ST_AsText(trazado) FROM rutas WHERE id = 1", String.class);
        paradaOriginal = jdbc.queryForMap("SELECT nombre, ST_AsText(ubicacion) AS ubicacion FROM paradas WHERE id = 1");
    }

    @AfterEach
    void restaurarOriginales() {
        jdbc.update("UPDATE rutas SET trazado = ST_GeomFromText(?, 4326) WHERE id = 1", trazadoOriginal);
        jdbc.update("UPDATE paradas SET nombre = ?, ubicacion = ST_GeomFromText(?, 4326) WHERE id = 1",
                paradaOriginal.get("nombre"), paradaOriginal.get("ubicacion"));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void lista_todas_las_rutas_con_paradas_y_trazado() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].paradas").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void corrige_el_trazado_respetando_latitud_y_longitud() throws Exception {
        mockMvc.perform(put("/api/v1/admin/rutas/1/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[
                                  {"latitud":14.6349,"longitud":-89.9812},
                                  {"latitud":14.6330,"longitud":-89.9850},
                                  {"latitud":14.6320,"longitud":-89.9880}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trazado", hasSize(3)))
                .andExpect(jsonPath("$.trazado[0].latitud").value(14.6349))
                .andExpect(jsonPath("$.trazado[0].longitud").value(-89.9812));

        // En PostGIS va (longitud, latitud): ADR-007.
        Double x = jdbc.queryForObject("SELECT ST_X(ST_StartPoint(trazado)) FROM rutas WHERE id = 1", Double.class);
        assertThat(x).isCloseTo(-89.9812, within(1e-9));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void rechaza_un_trazado_de_un_solo_punto_o_con_coordenadas_invertidas() throws Exception {
        mockMvc.perform(put("/api/v1/admin/rutas/1/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("{\"puntos\":[{\"latitud\":14.63,\"longitud\":-89.98}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/rutas/1/trazado")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"puntos":[{"latitud":-89.98,"longitud":14.63},
                                           {"latitud":-89.99,"longitud":14.64}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void corrige_nombre_y_ubicacion_de_una_parada() throws Exception {
        mockMvc.perform(put("/api/v1/admin/rutas/1/paradas/1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"  Parque Central (kiosco)  \",\"latitud\":14.6351,\"longitud\":-89.9813}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas[0].nombre").value("Parque Central (kiosco)"))
                .andExpect(jsonPath("$.paradas[0].latitud").value(14.6351));
    }

    @Test
    @WithMockUser(username = "uid-muni", roles = "ADMIN")
    void una_parada_de_otra_ruta_responde_404() throws Exception {
        Long ajena = jdbc.queryForObject("SELECT min(id) FROM paradas WHERE ruta_id <> 1", Long.class);
        mockMvc.perform(put("/api/v1/admin/rutas/1/paradas/" + ajena)
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"latitud\":14.63,\"longitud\":-89.98}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "conductor1", roles = "CONDUCTOR")
    void un_conductor_no_puede_corregir_rutas() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rutas")).andExpect(status().isForbidden());
    }
}
