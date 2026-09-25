package gt.muni.jalapa.ecoruta.seguridad;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.servicio.AltaDeEquipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-142, criterio (d): "el secreto no viaja en la URL ni queda en logs".
 *
 * <p>Se comprueba leyendo la salida real del proceso, no confiando en la
 * disciplina de quien escriba el proximo log.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretoNoSeFiltraIT extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private VehiculoRepository vehiculos;

    private AltaDeEquipo emitir() {
        Long bus = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        return equipoService.emitir(bus, "Tableta de pruebas");
    }

    @Test
    void el_secreto_no_aparece_en_los_logs_ni_al_aceptar_ni_al_rechazar(CapturedOutput salida)
            throws Exception {
        AltaDeEquipo equipo = emitir();
        String credencial = equipo.credencial().credencialCompleta();
        String secreto = equipo.credencial().secreto();

        // Una ingesta que funciona...
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credencial)
                        .contentType(APPLICATION_JSON)
                        .content(lote()))
                .andExpect(status().isAccepted());

        // ...y una que se rechaza, que es donde mas tienta escribir el token.
        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + equipo.credencial().codigoPublico()
                                + ".0123456789012345678901234567890123456789012")
                        .contentType(APPLICATION_JSON)
                        .content(lote()))
                .andExpect(status().isUnauthorized());

        assertThat(salida.getAll())
                .doesNotContain(secreto)
                .doesNotContain(credencial);
        // El codigo publico si: para eso el token es de dos partes.
        assertThat(salida.getAll()).contains(equipo.credencial().codigoPublico());
    }

    @Test
    void el_secreto_no_aparece_en_el_cuerpo_de_ningun_error(CapturedOutput salida) throws Exception {
        AltaDeEquipo equipo = emitir();
        String secreto = equipo.credencial().secreto();

        // El GlobalExceptionHandler vuelca ex.getMessage() al cuerpo, asi que
        // ninguna excepcion del camino de autenticacion puede llevar el token.
        String cuerpo = mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + equipo.credencial().codigoPublico()
                                + ".xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                        .contentType(APPLICATION_JSON)
                        .content(lote()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain(secreto)
                .doesNotContain("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        assertThat(salida.getAll()).doesNotContain(secreto);
    }

    @Test
    void el_hash_no_sale_por_ninguna_parte(CapturedOutput salida) throws Exception {
        AltaDeEquipo equipo = emitir();
        String hash = jdbc.queryForObject(
                "SELECT secreto_hash FROM equipos WHERE codigo_publico = ?",
                String.class, equipo.credencial().codigoPublico());

        mockMvc.perform(post("/api/v1/telemetria/posiciones")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + equipo.credencial().credencialCompleta())
                        .contentType(APPLICATION_JSON)
                        .content(lote()))
                .andExpect(status().isAccepted());

        assertThat(salida.getAll()).doesNotContain(hash);
    }

    @Test
    void el_alta_no_se_escribe_en_los_logs_al_serializarse(CapturedOutput salida) {
        AltaDeEquipo equipo = emitir();

        // Un record imprime todos sus componentes en el toString() generado. Si
        // alguien loguea el objeto entero, no puede salir la credencial.
        assertThat(equipo.toString())
                .doesNotContain(equipo.credencial().secreto())
                .contains("credencial=***");
        assertThat(salida.getAll()).doesNotContain(equipo.credencial().secreto());
    }

    private static String lote() {
        return """
                {"posiciones": [
                  {"latitud": 14.6335, "longitud": -89.9885, "velocidadKmh": 18, "timestamp": "%s"}
                ]}""".formatted(Instant.now());
    }
}
