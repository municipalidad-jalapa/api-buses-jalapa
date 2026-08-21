package gt.muni.jalapa.ecoruta.flota;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.telemetria.repositorio.PosicionHistoricaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-143 (HU-48): vincular un vehiculo con su equipo a bordo. */
class CambioDeEquipoConservaHistorialIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private PosicionHistoricaRepository posiciones;

    @Test
    void dar_de_baja_un_equipo_y_dar_de_alta_otro_no_pierde_el_historico_del_vehiculo()
            throws Exception {
        Vehiculo bus = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        AltaDeEquipo equipoA = equipoService.emitir(bus.getId(), "Tableta A");

        ingestar(equipoA, 14.6300, -89.9800, Instant.now().minus(Duration.ofMinutes(10)));
        ingestar(equipoA, 14.6310, -89.9810, Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(posiciones.countByVehiculoId(bus.getId())).isEqualTo(2);

        // Se cambia el aparato: es un tramite de datos, no una migracion.
        String cuerpo = mockMvc.perform(post("/api/v1/admin/vehiculos/" + bus.getId() + "/equipos")
                        .header("X-Admin-Token", ADMIN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"etiqueta": "Tableta B"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String credencialB = cuerpo.replaceAll(".*\"credencial\":\"([^\"]+)\".*", "$1");

        // El equipo viejo ya no sirve...
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + equipoA.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6320, -89.9820, Instant.now())))
                .andExpect(status().isUnauthorized());

        // ...y el nuevo si.
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credencialB)
                        .contentType(APPLICATION_JSON)
                        .content(lote(14.6330, -89.9830, Instant.now())))
                .andExpect(status().isAccepted());

        // El historico del BUS-01 tiene los tres tramos, los de antes y el de ahora.
        assertThat(posiciones.countByVehiculoId(bus.getId())).isEqualTo(3);

        // Y las filas viejas siguen atribuidas al equipo A: ni nulas ni huerfanas.
        Long conEquipoA = jdbc.queryForObject(
                "SELECT count(*) FROM posiciones_historicas WHERE equipo_id = ?",
                Long.class, equipoA.equipoId());
        assertThat(conEquipoA).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM posiciones_historicas WHERE vehiculo_id IS NULL", Long.class))
                .isZero();
    }

    @Test
    void cada_posicion_se_atribuye_al_vehiculo_de_su_propio_equipo() throws Exception {
        Vehiculo bus1 = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        Vehiculo bus2 = vehiculos.save(new Vehiculo("BUS-99", "P-999XXX"));

        AltaDeEquipo equipo1 = equipoService.emitir(bus1.getId(), "Tableta BUS-01");
        AltaDeEquipo equipo2 = equipoService.emitir(bus2.getId(), "Tableta BUS-99");

        ingestar(equipo1, 14.6300, -89.9800, Instant.now().minus(Duration.ofMinutes(2)));
        ingestar(equipo2, 14.7000, -89.7000, Instant.now());

        assertThat(posiciones.countByVehiculoId(bus1.getId())).isEqualTo(1);
        assertThat(posiciones.countByVehiculoId(bus2.getId())).isEqualTo(1);

        mockMvc.perform(get("/api/v1/telemetria/posicion").param("vehiculoId", bus1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitud").value(14.6300))
                .andExpect(jsonPath("$.vehiculo").value("BUS-01"));

        mockMvc.perform(get("/api/v1/telemetria/posicion").param("vehiculoId", bus2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitud").value(14.7000))
                .andExpect(jsonPath("$.vehiculo").value("BUS-99"));
    }

    @Test
    void un_equipo_no_puede_reportar_a_nombre_de_otro_bus() throws Exception {
        // El vehiculo sale de la credencial, no del cuerpo: aunque el equipo mande
        // un vehiculoId, se ignora.
        Vehiculo bus1 = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        Vehiculo bus2 = vehiculos.save(new Vehiculo("BUS-98", "P-988XXX"));
        AltaDeEquipo equipo1 = equipoService.emitir(bus1.getId(), "Tableta BUS-01");

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + equipo1.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"vehiculoId": %d, "posiciones": [
                                  {"latitud": 14.63, "longitud": -89.98, "timestamp": "%s"}
                                ]}""".formatted(bus2.getId(), Instant.now())))
                .andExpect(status().isAccepted());

        assertThat(posiciones.countByVehiculoId(bus1.getId())).isEqualTo(1);
        assertThat(posiciones.countByVehiculoId(bus2.getId())).isZero();
    }

    private void ingestar(AltaDeEquipo equipo, double lat, double lon, Instant cuando)
            throws Exception {
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content(lote(lat, lon, cuando)))
                .andExpect(status().isAccepted());
    }

    private static String lote(double lat, double lon, Instant cuando) {
        return """
                {"posiciones": [
                  {"latitud": %s, "longitud": %s, "velocidadKmh": 20, "timestamp": "%s"}
                ]}""".formatted(lat, lon, cuando);
    }
}
