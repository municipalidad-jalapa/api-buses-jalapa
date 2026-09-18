package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
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
import static org.mockito.Mockito.when;

/**
 * HU-124: cancelacion de la reserva, sin levantar Spring.
 *
 * <p>Portada de {@code DemandaServiceTest} de la rama de SCRUM-276, que cancelaba
 * sobre un dominio paralelo en {@code /api/v1/demanda/registros}. Se conservan
 * sus cinco casos y se agrega uno que esa logica rechazaba por error: solo dejaba
 * cancelar desde ACTIVO, asi que una reserva ya renovada no se podia soltar.
 */
@ExtendWith(MockitoExtension.class)
class ReservaCancelacionTest {

    private static final Instant AHORA = Instant.parse("2026-08-31T14:00:00Z");
    private static final String DUENO = "dispositivo-uno";

    @Mock
    private ReservaRepository reservas;

    @Mock
    private ParadaRepository paradas;

    private ReservaService servicio;

    @BeforeEach
    void armarServicio() {
        DemandaProperties demanda = new DemandaProperties(10, 5, 150);
        servicio = new ReservaService(reservas, paradas, demanda, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void cancelar_una_reserva_vigente_la_deja_cancelada_con_su_fecha() {
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.plus(3, ChronoUnit.MINUTES));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        servicio.cancelar(1L, DUENO);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.getCanceladoEn()).isEqualTo(AHORA);
    }

    @Test
    void una_reserva_ya_renovada_tambien_se_puede_cancelar() {
        Reserva reserva = reserva(EstadoReserva.RENOVADA, AHORA.plus(3, ChronoUnit.MINUTES));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        servicio.cancelar(1L, DUENO);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void no_permite_cancelar_la_reserva_de_otro_dispositivo() {
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.plus(3, ChronoUnit.MINUTES));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.cancelar(1L, "otro-dispositivo"))
                .isInstanceOf(AccessDeniedException.class);
        // La reserva ajena queda intacta.
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test
    void cancelar_una_reserva_inexistente_falla_con_404() {
        when(reservas.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelar(1L, DUENO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void no_permite_cancelar_dos_veces_la_misma_reserva() {
        Reserva reserva = reserva(EstadoReserva.CANCELADA, AHORA.plus(3, ChronoUnit.MINUTES));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.cancelar(1L, DUENO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Esta reserva ya estaba cancelada.");
    }

    @Test
    void no_permite_cancelar_una_reserva_cuya_vigencia_ya_vencio() {
        // Aun sin que la tarea programada la haya marcado EXPIRADA.
        Reserva reserva = reserva(EstadoReserva.ACTIVA, AHORA.minusSeconds(1));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.cancelar(1L, DUENO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Esta reserva ya venció o no está activa.");
    }

    @Test
    void no_permite_cancelar_una_reserva_ya_expirada() {
        Reserva reserva = reserva(EstadoReserva.EXPIRADA, AHORA.minusSeconds(60));
        when(reservas.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> servicio.cancelar(1L, DUENO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    private static Reserva reserva(EstadoReserva estado, Instant expiraEn) {
        Parada parada = new Parada();
        parada.setId(1L);
        Reserva reserva = new Reserva(DUENO, parada, estado, AHORA.minusSeconds(60), expiraEn);
        reserva.setId(1L);
        return reserva;
    }
}
