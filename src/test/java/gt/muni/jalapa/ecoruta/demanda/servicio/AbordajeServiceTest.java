package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.FuenteAbordaje;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbordajeServiceTest {

    @Mock
    private ReservaRepository reservas;

    @InjectMocks
    private AbordajeService abordaje;

    @Test
    void el_pasajero_que_sube_deja_la_reserva_en_ABORDO() {
        Reserva reserva = activa(7L);
        when(reservas.findById(7L)).thenReturn(Optional.of(reserva));

        var respuesta = abordaje.registrarPasajero(7L, "dev", true);

        assertThat(respuesta.estado()).isEqualTo("ABORDO");
        assertThat(reserva.getSubio()).isTrue();
        assertThat(reserva.getAbordajeFuente()).isEqualTo(FuenteAbordaje.PASAJERO);
    }

    @Test
    void el_conductor_sobrescribe_la_respuesta_del_pasajero() {
        Reserva reserva = activa(8L);
        when(reservas.findById(8L)).thenReturn(Optional.of(reserva));
        abordaje.registrarPasajero(8L, "dev", true);

        var respuesta = abordaje.registrarConductor(8L, false);

        assertThat(respuesta.estado()).isEqualTo("CANCELADA");
        assertThat(reserva.getSubio()).isFalse();
        assertThat(reserva.getAbordajeFuente()).isEqualTo(FuenteAbordaje.CONDUCTOR);
    }

    @Test
    void el_pasajero_no_puede_abordar_una_reserva_ya_cerrada() {
        Reserva reserva = activa(9L);
        reserva.setEstado(EstadoReserva.ABORDO);
        when(reservas.findById(9L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> abordaje.registrarPasajero(9L, "dev", false))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("La reserva ya no esta activa");
    }

    @Test
    void el_pasajero_no_puede_responder_por_la_reserva_de_otro_dispositivo() {
        Reserva reserva = activa(10L);
        when(reservas.findById(10L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> abordaje.registrarPasajero(10L, "intruso", true))
                .isInstanceOf(AccessDeniedException.class);
        // La reserva ajena queda intacta.
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    private static Reserva activa(Long id) {
        Reserva reserva = new Reserva();
        reserva.setId(id);
        reserva.setDispositivoId("dev");
        Parada parada = new Parada();
        parada.setId(1L);
        reserva.setParada(parada);
        reserva.setEstado(EstadoReserva.ACTIVA);
        reserva.setExpiraEn(Instant.now().plusSeconds(3600));
        return reserva;
    }
}
