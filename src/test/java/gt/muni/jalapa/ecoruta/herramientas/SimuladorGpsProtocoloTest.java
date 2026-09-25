package gt.muni.jalapa.ecoruta.herramientas;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Formato tk103/gps103 contra ejemplos de los tests del decoder de Traccar
 * v6.15.3 (Tk103ProtocolDecoderTest, Gps103ProtocolDecoderTest).
 */
class SimuladorGpsProtocoloTest {

    @Test
    void nmea_reproduce_las_coordenadas_del_ejemplo_tk103() {
        // (352606090042050,BP05,240414,A,4527.3513N,00909.9758E,...)
        // position("2014-04-24 11:28:25.000", true, 45.45586, 9.16626)
        assertThat(SimuladorGps.nmea(45.45586, true)).startsWith("4527.351");
        assertThat(SimuladorGps.nmea(45.45586, true)).endsWith("N");
        assertThat(SimuladorGps.nmea(9.16626, false)).startsWith("00909.975");
        assertThat(SimuladorGps.nmea(9.16626, false)).endsWith("E");
    }

    @Test
    void nmea_reproduce_las_coordenadas_del_ejemplo_gps103() {
        // imei:...,A,5443.3834,N,02512.9071,E,...
        // position("2015-10-30 00:01:01.000", true, 54.72306, 25.21512)
        assertThat(SimuladorGps.nmea(54.72306, true)).startsWith("5443.383");
        assertThat(SimuladorGps.nmea(25.21512, false)).startsWith("02512.907");
    }

    @Test
    void el_mensaje_tk103_sigue_el_patron_coma_delimitado_del_decoder() {
        Instant cuando = LocalDateTime.of(2014, 4, 24, 11, 28, 25).toInstant(ZoneOffset.UTC);
        String msg = SimuladorGps.mensajeTk103("352606090042050", cuando, 45.45586, 9.16626, 4.80, true);

        assertThat(msg).startsWith("(").endsWith(")");
        assertThat(msg).contains("352606090042050");
        assertThat(msg).contains(",BR00,");
        assertThat(msg).contains(",240414,");
        assertThat(msg).contains(",A,");
        assertThat(msg).contains("4527.351");
        assertThat(msg).contains("N,");
        assertThat(msg).contains("00909.975");
        assertThat(msg).contains("E,");
        assertThat(msg).contains(",112825,");
        assertThat(msg).matches(Pattern.compile("\\([^)]+,BR00,\\d{6},A,[^,]+,[^,]+,\\d+\\.\\d+,\\d{6},[^)]+\\)"));
    }

    @Test
    void el_mensaje_tk103_sin_fijo_lleva_validez_V() {
        String msg = SimuladorGps.mensajeTk103("860000000000001", Instant.parse("2026-09-20T15:00:00Z"),
                14.6335, -89.9885, 0, false);
        assertThat(msg).contains(",V,");
        assertThat(msg).doesNotContain(",A,");
    }

    @Test
    void el_mensaje_gps103_sigue_el_patron_del_decoder() {
        Instant cuando = LocalDateTime.of(2015, 10, 30, 0, 1, 1).toInstant(ZoneOffset.UTC);
        String msg = SimuladorGps.mensajeGps103("359710045559474", cuando, 54.72306, 25.21512, 0, true);

        assertThat(msg).startsWith("imei:359710045559474,tracker,");
        assertThat(msg).contains("151030000101");
        assertThat(msg).contains(",F,000101.000,A,");
        assertThat(msg).contains("5443.383");
        assertThat(msg).contains(",N,");
        assertThat(msg).contains("02512.907");
        assertThat(msg).contains(",E,");
        assertThat(msg).endsWith(";");
    }

    @Test
    void gps103_convierte_kmh_a_nudos() {
        Instant t = Instant.parse("2026-09-20T12:00:00Z");
        String msg = SimuladorGps.mensajeGps103("860000000000001", t, 14.63, -89.98, 18.52, true);
        // 18.52 km/h = 10 nudos
        assertThat(msg).contains(",10.00,");
    }

    @Test
    void el_protocolo_por_defecto_es_tk103_en_el_puerto_5002() {
        assertThat(SimuladorGps.puertoPorDefecto("tk103")).isEqualTo(5002);
        assertThat(SimuladorGps.puertoPorDefecto("gps103")).isEqualTo(5001);
        Instant t = Instant.parse("2026-09-20T12:00:00Z");
        assertThat(SimuladorGps.mensajeProtocolo("tk103", "1", t, 1, 1, 0, true)).startsWith("(");
        assertThat(SimuladorGps.mensajeProtocolo("gps103", "1", t, 1, 1, 0, true)).startsWith("imei:");
    }

    @Test
    void nmea_de_jalapa_queda_al_norte_y_al_oeste() {
        assertThat(SimuladorGps.nmea(14.6335, true)).endsWith("N");
        assertThat(SimuladorGps.nmea(-89.9885, false)).endsWith("W");
        assertThat(minutosDe(SimuladorGps.nmea(14.6335, true))).isCloseTo(38.01, within(0.01));
    }

    private static double minutosDe(String nmea) {
        String cuerpo = nmea.substring(0, nmea.length() - 1);
        return Double.parseDouble(cuerpo.substring(cuerpo.length() == 9 ? 2 : 3));
    }
}
