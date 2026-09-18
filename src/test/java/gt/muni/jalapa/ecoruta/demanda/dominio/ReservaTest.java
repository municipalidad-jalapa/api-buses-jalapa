package gt.muni.jalapa.ecoruta.demanda.dominio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de vigencia y transiciones de {@link Reserva} (HU-135). */
class ReservaTest {

    private static final Instant AHORA = Instant.parse("2026-08-31T14:00:00Z");

    @Test
    void una_reserva_activa_con_expiracion_futura_esta_vigente() {
        Reserva reserva = activa(AHORA.plus(5, ChronoUnit.MINUTES));

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(reserva.estaVigente(AHORA)).isTrue();
    }

    @Test
    void una_reserva_cuya_expiracion_ya_paso_no_esta_vigente() {
        Reserva reserva = activa(AHORA.minusSeconds(1));

        assertThat(reserva.estaVigente(AHORA)).isFalse();
    }

    @Test
    void una_reserva_que_no_esta_en_estado_renovable_no_esta_vigente_aunque_no_haya_expirado() {
        Reserva reserva = activa(AHORA.plus(5, ChronoUnit.MINUTES));
        reserva.setEstado(EstadoReserva.CANCELADA);

        assertThat(reserva.estaVigente(AHORA)).isFalse();
    }

    @Test
    void renovar_extiende_la_expiracion_y_deja_la_reserva_renovada() {
        Reserva reserva = activa(AHORA.plus(1, ChronoUnit.MINUTES));

        Instant nuevo = AHORA.plus(5, ChronoUnit.MINUTES);
        reserva.renovar(nuevo);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.RENOVADA);
        assertThat(reserva.getExpiraEn()).isEqualTo(nuevo);
        // Una reserva RENOVADA sigue siendo renovable.
        assertThat(reserva.estaVigente(AHORA)).isTrue();
    }

    @Test
    void expirar_pasa_la_reserva_a_expirada() {
        Reserva reserva = activa(AHORA);

        reserva.expirar();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(reserva.estaVigente(AHORA.minusSeconds(30))).isFalse();
    }

    /**
     * Espejo en codigo del indice unico parcial de V7.
     *
     * <p>La rama original de HU-135 contaba tambien ABORDO en el cupo. Como la
     * tarea programada solo expira ACTIVA y RENOVADA, una reserva en ABORDO no
     * venceria nunca y el pasajero que subio una vez no podria volver a reservar.
     */
    @Test
    void solo_activa_y_renovada_son_vigentes_abordo_no_ocupa_el_cupo() {
        assertThat(EstadoReserva.RENOVABLES)
                .containsExactlyInAnyOrder(EstadoReserva.ACTIVA, EstadoReserva.RENOVADA)
                .doesNotContain(EstadoReserva.ABORDO);
    }

    private static Reserva activa(Instant expiraEn) {
        Parada parada = new Parada();
        parada.setId(1L);
        return new Reserva("disp", parada, EstadoReserva.ACTIVA, AHORA.minusSeconds(60), expiraEn);
    }
}
