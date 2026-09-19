package gt.muni.jalapa.ecoruta.precision.servicio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculadorDeErrorTest {

    @Test
    void el_error_es_la_diferencia_absoluta_en_minutos() {
        Instant predichoEn = Instant.parse("2026-09-13T12:00:00Z");
        Instant llegadaEn = Instant.parse("2026-09-13T12:12:00Z");

        // Predijo 8 minutos → llegada esperada 12:08. Real 12:12. Error = 4.
        assertThat(CalculadorDeError.minutos(predichoEn, 8, llegadaEn)).isCloseTo(4.0, within(0.001));
    }
}
