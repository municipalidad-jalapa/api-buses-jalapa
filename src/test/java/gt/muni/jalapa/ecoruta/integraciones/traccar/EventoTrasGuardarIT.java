package gt.muni.jalapa.ecoruta.integraciones.traccar;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.servicio.EquipoService;
import gt.muni.jalapa.ecoruta.telemetria.servicio.LecturaEntrante;
import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(EventoTrasGuardarIT.Oyente.class)
class EventoTrasGuardarIT extends IntegracionPostgisTest {

    @Autowired
    private Oyente oyente;

    @Autowired
    private TelemetriaService telemetria;

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private EquipoRepository equipos;

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private TransactionTemplate transaccion;

    private Long equipoId;

    @BeforeEach
    void preparar() {
        oyente.recibidos.clear();
        Long bus = vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
        equipoService.emitir(bus, "GPS de prueba");
        equipoId = jdbc.queryForObject(
                "SELECT id FROM equipos WHERE vehiculo_id = ? AND estado = 'ACTIVO'", Long.class, bus);
        jdbc.update("INSERT INTO dispositivos_externos (identificador, equipo_id) VALUES (?, ?)",
                RecepcionTraccarIT.IMEI, equipoId);
    }

    @Test
    void el_reenvio_de_traccar_publica_la_posicion_despues_de_guardarla() throws Exception {
        mockMvc.perform(post(RecepcionTraccarIT.RUTA)
                        .header("X-Traccar-Token", TRACCAR)
                        .contentType(APPLICATION_JSON)
                        .content(RecepcionTraccarIT.reenvio(1, RecepcionTraccarIT.IMEI, 10, Instant.now())))
                .andExpect(status().isAccepted());

        assertThat(oyente.recibidos).hasSize(1);
        assertThat(oyente.recibidos.getFirst().posicion().vehiculo()).isEqualTo("BUS-01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Integer.class)).isEqualTo(1);
    }

    @Test
    void si_el_guardado_se_revierte_no_se_publica_nada() {
        transaccion.executeWithoutResult(estado -> {
            Equipo equipo = equipos.findById(equipoId).orElseThrow();
            telemetria.registrar(equipo, List.of(
                    new LecturaEntrante(14.6335, -89.9885, 20.0, Instant.now(), "traccar:rollback")));
            estado.setRollbackOnly();
        });

        assertThat(oyente.recibidos).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posiciones_historicas", Integer.class)).isZero();
    }

    @TestConfiguration
    static class Oyente {

        final List<PosicionVigenteActualizada> recibidos = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void alActualizar(PosicionVigenteActualizada evento) {
            recibidos.add(evento);
        }
    }
}
