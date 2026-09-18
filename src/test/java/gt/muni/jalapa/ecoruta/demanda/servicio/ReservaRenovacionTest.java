package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-135: renovacion y expiracion de la reserva, sin levantar Spring.
 *
 * <p>Portada de {@code DemandaServiceTest} de la rama original al integrar el
 * sprint 5. Los casos de creacion que traia no se portaron: los cubre
 * {@link ReservaServiceTest}, y ademas con la geocerca que HU-135 no tenia.
 */
@ExtendWith(MockitoExtension.class)
class ReservaRenovacionTest {

    private static final Instant AHORA = Instant.parse("2026-08-31T14:00:00Z");
    private static final int TTL_MINUTOS = 5;

    @Mock
    private ReservaRepository reservas;

    @Mock
    private ParadaRepository paradas;

    private ReservaService servicio;

    @BeforeEach
    void armarServicio() {
        DemandaProperties demanda = new DemandaProperties(10, TTL_MINUTOS, 150);
        servicio = new ReservaService(reservas, paradas, demanda, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void renovar_una_reserva_vigente_la_extiende_otro_periodo_y_la_deja_renovada() {
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.plus(1, ChronoUnit.MINUTES));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        ReservaResponse renovada = servicio.renovar(7L, "disp");

        assertThat(renovada.estado()).isEqualTo(EstadoReserva.RENOVADA);
        assertThat(renovada.expiraEn()).isEqualTo(AHORA.plus(TTL_MINUTOS, ChronoUnit.MINUTES));
        // Conserva su identificador: no se crea una reserva nueva.
        assertThat(renovada.id()).isEqualTo(7L);
    }

    @Test
    void una_reserva_ya_renovada_se_puede_renovar_otra_vez() {
        Reserva reserva = reserva(EstadoReserva.RENOVADA, AHORA.plus(1, ChronoUnit.MINUTES));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        assertThat(servicio.renovar(7L, "disp").estado()).isEqualTo(EstadoReserva.RENOVADA);
    }

    @Test
    void renovar_una_reserva_inexistente_falla_con_404() {
        when(reservas.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.renovar(7L, "disp"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void renovar_una_reserva_ya_vencida_falla_con_422() {
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.minusSeconds(1));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.renovar(7L, "disp"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Esta reserva ya venció o no está activa.");
    }

    @Test
    void renovar_una_reserva_que_no_esta_vigente_falla_con_422() {
        Reserva reserva = reserva(EstadoReserva.CANCELADA, AHORA.plus(1, ChronoUnit.MINUTES));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.renovar(7L, "disp"))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void no_permite_renovar_la_reserva_de_otro_dispositivo() {
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.plus(1, ChronoUnit.MINUTES));
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.renovar(7L, "intruso"))
                .isInstanceOf(AccessDeniedException.class);
        // No se extendio la vigencia de la reserva ajena.
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test
    void expirar_vencidas_delega_el_barrido_masivo_sobre_los_estados_vigentes() {
        when(reservas.marcarExpiradas(EstadoReserva.RENOVABLES, AHORA)).thenReturn(3);

        assertThat(servicio.expirarVencidas()).isEqualTo(3);
        verify(reservas).marcarExpiradas(EstadoReserva.RENOVABLES, AHORA);
    }

    private static Reserva reserva(EstadoReserva estado, Instant expiraEn) {
        Parada parada = new Parada();
        parada.setId(1L);
        Reserva reserva = new Reserva("disp", parada, estado, AHORA.minusSeconds(60), expiraEn);
        reserva.setId(7L);
        return reserva;
    }
}
