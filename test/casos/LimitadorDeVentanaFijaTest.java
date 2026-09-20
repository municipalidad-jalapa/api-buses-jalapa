package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** HU Desarrollo-95: el contador de ventana fija, sin Spring ni Docker. */
class LimitadorDeVentanaFijaTest {

    private static final Instant INICIO = Instant.parse("2026-09-18T10:00:00Z");

    private RelojAjustable reloj;

    @BeforeEach
    void armarReloj() {
        reloj = new RelojAjustable(INICIO);
    }

    @Test
    void permite_peticiones_hasta_la_capacidad() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(3, 60, reloj);

        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
    }

    @Test
    void rechaza_al_superar_la_capacidad_dentro_de_la_misma_ventana() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(3, 60, reloj);

        limitador.intentar("ip-1");
        limitador.intentar("ip-1");
        limitador.intentar("ip-1");

        assertThat(limitador.intentar("ip-1").permitido()).isFalse();
    }

    @Test
    void informa_cuanto_falta_para_poder_reintentar() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(1, 30, reloj);

        limitador.intentar("ip-1");
        reloj.avanzar(Duration.ofSeconds(10));

        LimitadorDeVentanaFija.Resultado resultado = limitador.intentar("ip-1");

        assertThat(resultado.permitido()).isFalse();
        assertThat(resultado.segundosParaReintentar()).isEqualTo(20);
    }

    @Test
    void cada_clave_tiene_su_propio_cupo() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(1, 60, reloj);

        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
        assertThat(limitador.intentar("ip-1").permitido()).isFalse();
        // Otra clave (otra IP, u otro dispositivo) no comparte el cupo de la primera.
        assertThat(limitador.intentar("ip-2").permitido()).isTrue();
    }

    @Test
    void reinicia_el_cupo_cuando_la_ventana_termina() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(1, 30, reloj);

        limitador.intentar("ip-1");
        assertThat(limitador.intentar("ip-1").permitido()).isFalse();

        reloj.avanzar(Duration.ofSeconds(30));

        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
    }

    @Test
    void limpiar_no_rompe_una_clave_todavia_activa() {
        LimitadorDeVentanaFija limitador = new LimitadorDeVentanaFija(2, 60, reloj);

        limitador.intentar("ip-1");
        limitador.limpiar();

        assertThat(limitador.intentar("ip-1").permitido()).isTrue();
        assertThat(limitador.intentar("ip-1").permitido()).isFalse();
    }

    /** Clock mutable de prueba: avanza a mano sin depender del reloj real. */
    private static final class RelojAjustable extends Clock {
        private Instant instante;

        RelojAjustable(Instant inicial) {
            this.instante = inicial;
        }

        void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
