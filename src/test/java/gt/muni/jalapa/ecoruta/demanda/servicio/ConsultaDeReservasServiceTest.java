package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservasDeParadaResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La consulta no mira estados en memoria: pide el subconjunto vigente al
 * repositorio. Estas pruebas fijan esa frontera, no el SQL.
 */
@ExtendWith(MockitoExtension.class)
class ConsultaDeReservasServiceTest {

    private static final Long PARADA = 7L;

    @Mock
    private ParadaRepository paradaRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private ConsultaDeReservasService servicio;

    @Test
    void parada_inexistente_lanza_RecursoNoEncontradoException() {
        when(paradaRepository.existsById(PARADA)).thenReturn(false);

        assertThatThrownBy(() -> servicio.consultar(PARADA))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Parada con id 7 no existe");

        verify(reservaRepository, never())
                .findByParadaIdAndEstadoInOrderByExpiraEnAsc(anyLong(), any());
    }

    @Test
    void parada_sin_reservas_vigentes_devuelve_activas_cero_y_lista_vacia() {
        when(paradaRepository.existsById(PARADA)).thenReturn(true);
        when(reservaRepository.findByParadaIdAndEstadoInOrderByExpiraEnAsc(
                PARADA, EstadoReserva.vigentes()))
                .thenReturn(List.of());

        ReservasDeParadaResponse respuesta = servicio.consultar(PARADA);

        assertThat(respuesta.paradaId()).isEqualTo(PARADA);
        assertThat(respuesta.activas()).isZero();
        assertThat(respuesta.reservas()).isEmpty();
    }

    @Test
    void solo_pide_al_repositorio_el_subconjunto_vigente() {
        Instant t1 = Instant.parse("2026-08-27T12:00:00Z");
        Instant t2 = Instant.parse("2026-08-27T12:10:00Z");
        Instant t3 = Instant.parse("2026-08-27T12:20:00Z");
        Reserva abordo = reserva(1L, EstadoReserva.ABORDO, t1);
        Reserva renovada = reserva(2L, EstadoReserva.RENOVADA, t2);
        Reserva activa = reserva(3L, EstadoReserva.ACTIVA, t3);

        when(paradaRepository.existsById(PARADA)).thenReturn(true);
        when(reservaRepository.findByParadaIdAndEstadoInOrderByExpiraEnAsc(
                PARADA, EstadoReserva.vigentes()))
                .thenReturn(List.of(abordo, renovada, activa));

        ReservasDeParadaResponse respuesta = servicio.consultar(PARADA);

        assertThat(EstadoReserva.vigentes()).containsExactlyInAnyOrder(
                EstadoReserva.ACTIVA, EstadoReserva.RENOVADA, EstadoReserva.ABORDO);
        assertThat(EstadoReserva.vigentes()).doesNotContain(
                EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA);

        assertThat(respuesta.activas()).isEqualTo(3);
        assertThat(respuesta.reservas()).extracting(r -> r.id())
                .containsExactly(1L, 2L, 3L);
        assertThat(respuesta.reservas()).extracting(r -> r.expiraEn())
                .containsExactly(t1, t2, t3);

        verify(reservaRepository).findByParadaIdAndEstadoInOrderByExpiraEnAsc(
                PARADA, EstadoReserva.vigentes());
    }

    private static Reserva reserva(Long id, EstadoReserva estado, Instant expiraEn) {
        Reserva reserva = new Reserva();
        reserva.setId(id);
        reserva.setParadaId(PARADA);
        reserva.setEstado(estado);
        reserva.setExpiraEn(expiraEn);
        return reserva;
    }
}
