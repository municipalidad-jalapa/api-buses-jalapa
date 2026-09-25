package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Calculo de vigencia y reglas de renovacion/expiracion (Desarrollo-135). */
@ExtendWith(MockitoExtension.class)
class DemandaServiceTest {

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ParadaRepository paradas;

    private DemandaService servicio;

    @BeforeEach
    void init() {
        servicio = new DemandaService(reservas, paradas, new DemandaProperties(5, 60));
    }

    @Test
    void crear_fija_la_expiracion_cinco_minutos_despues_y_deja_la_reserva_activa() {
        when(paradas.existsById(1L)).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq("disp"), any())).thenReturn(false);
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant antes = Instant.now();
        Reserva creada = servicio.crear("disp", 1L);
        Instant despues = Instant.now();

        assertThat(creada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(creada.getExpiraEn())
                .isBetween(antes.plus(Duration.ofMinutes(5)), despues.plus(Duration.ofMinutes(5)));
    }

    @Test
    void crear_falla_con_404_si_la_parada_no_existe() {
        when(paradas.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear("disp", 99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(reservas, never()).save(any());
    }

    @Test
    void crear_falla_con_422_si_el_dispositivo_ya_tiene_una_reserva_que_ocupa_cupo() {
        when(paradas.existsById(1L)).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn("disp", EstadoReserva.OCUPAN_CUPO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear("disp", 1L))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(reservas, never()).save(any());
    }

    @Test
    void renovar_una_reserva_vigente_la_extiende_otros_cinco_minutos_y_la_deja_renovada() {
        Reserva reserva = new Reserva("disp", 1L, Instant.now().plusSeconds(30));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        Instant antes = Instant.now();
        Reserva renovada = servicio.renovar(7L);

        assertThat(renovada.getEstado()).isEqualTo(EstadoReserva.RENOVADA);
        assertThat(renovada.getExpiraEn()).isAfter(antes.plus(Duration.ofMinutes(4)));
    }

    @Test
    void renovar_una_reserva_inexistente_falla_con_404() {
        when(reservas.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.renovar(7L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void renovar_una_reserva_ya_expirada_falla_con_422() {
        Reserva expirada = new Reserva("disp", 1L, Instant.now().minusSeconds(1));
        when(reservas.findById(7L)).thenReturn(Optional.of(expirada));

        assertThatThrownBy(() -> servicio.renovar(7L))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(expirada.getEstado()).isEqualTo(EstadoReserva.ACTIVA); // no la toca
    }

    @Test
    void renovar_una_reserva_que_no_esta_activa_falla_con_422() {
        Reserva cancelada = new Reserva("disp", 1L, Instant.now().plusSeconds(120));
        cancelada.setEstado(EstadoReserva.CANCELADA);
        when(reservas.findById(7L)).thenReturn(Optional.of(cancelada));

        assertThatThrownBy(() -> servicio.renovar(7L))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void expirar_vencidas_delega_el_barrido_masivo_sobre_los_estados_renovables() {
        when(reservas.marcarExpiradas(any(), any())).thenReturn(3);

        int cuantas = servicio.expirarVencidas();

        assertThat(cuantas).isEqualTo(3);
        ArgumentCaptor<Instant> ahora = ArgumentCaptor.forClass(Instant.class);
        verify(reservas).marcarExpiradas(eq(EstadoReserva.RENOVABLES), ahora.capture());
        assertThat(ahora.getValue()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
    }
}
