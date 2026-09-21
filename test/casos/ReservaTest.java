package gt.muni.jalapa.ecoruta.demanda.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de vigencia y transiciones de {@link Reserva} (Desarrollo-135). */
class ReservaTest {

    private static final Instant AHORA = Instant.parse("2026-08-31T14:00:00Z");

    @Test
    void una_reserva_activa_con_expiracion_futura_esta_vigente() {
        Reserva reserva = new Reserva("disp", 1L, AHORA.plus(5, ChronoUnit.MINUTES));

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(reserva.estaVigente(AHORA)).isTrue();
    }

    @Test
    void una_reserva_cuya_expiracion_ya_paso_no_esta_vigente() {
        Reserva reserva = new Reserva("disp", 1L, AHORA.minusSeconds(1));

        assertThat(reserva.estaVigente(AHORA)).isFalse();
    }

    @Test
    void una_reserva_que_no_esta_en_estado_renovable_no_esta_vigente_aunque_no_haya_expirado() {
        Reserva reserva = new Reserva("disp", 1L, AHORA.plus(5, ChronoUnit.MINUTES));
        reserva.setEstado(EstadoReserva.CANCELADA);

        assertThat(reserva.estaVigente(AHORA)).isFalse();
    }

    @Test
    void renovar_extiende_la_expiracion_y_deja_la_reserva_renovada() {
        Reserva reserva = new Reserva("disp", 1L, AHORA.plus(1, ChronoUnit.MINUTES));

        Instant nuevo = AHORA.plus(5, ChronoUnit.MINUTES);
        reserva.renovar(nuevo);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.RENOVADA);
        assertThat(reserva.getExpiraEn()).isEqualTo(nuevo);
        // Una reserva RENOVADA sigue siendo renovable.
        assertThat(reserva.estaVigente(AHORA)).isTrue();
    }

    @Test
    void expirar_pasa_la_reserva_a_expirada() {
        Reserva reserva = new Reserva("disp", 1L, AHORA);

        reserva.expirar();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(reserva.estaVigente(AHORA.minusSeconds(30))).isFalse();
    }

    @Test
    void los_conjuntos_de_estados_reflejan_el_indice_parcial() {
        assertThat(EstadoReserva.RENOVABLES)
                .containsExactlyInAnyOrder(EstadoReserva.ACTIVA, EstadoReserva.RENOVADA);
        assertThat(EstadoReserva.OCUPAN_CUPO)
                .containsExactlyInAnyOrder(EstadoReserva.ACTIVA, EstadoReserva.RENOVADA, EstadoReserva.ABORDO);
    }
}
