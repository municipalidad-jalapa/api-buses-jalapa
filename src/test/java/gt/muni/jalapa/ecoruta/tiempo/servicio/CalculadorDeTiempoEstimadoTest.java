package gt.muni.jalapa.ecoruta.tiempo.servicio;

import gt.muni.jalapa.ecoruta.tiempo.dominio.ParametrosDeRuta;
import gt.muni.jalapa.ecoruta.tiempo.dominio.TipoDeDia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-72, criterio 7: con parametros conocidos y una posicion conocida, el
 * minuto calculado coincide con el valor esperado.
 */
class CalculadorDeTiempoEstimadoTest {

    private final CalculadorDeTiempoEstimado calculador = new CalculadorDeTiempoEstimado();

    /**
     * Cuentas a mano, para que el assert no sea un eco del codigo:
     *
     * <pre>
     *   espera hasta las 08:00 = 10 min = 600 s
     *   trayecto               = 44 min = 2640 s
     *   2 paradas intermedias  = 2 * 18 s = 36 s
     *   total                  = 3276 s → ceil(54.6) = 55 min
     * </pre>
     */
    @Test
    void con_parametros_y_posicion_conocidos_el_minuto_es_el_esperado() {
        ParametrosDeRuta parametros = parametrosConocidos();
        List<PuntoDeParada> paradas = paradasConocidas();
        // Encima del origen: el bus todavia no arranco.
        double latitud = 14.634878;
        double longitud = -89.981202;
        ZonedDateTime ahora = ZonedDateTime.of(
                LocalDate.of(2026, 9, 14), LocalTime.of(7, 50), CalculadorDeTiempoEstimado.ZONA);

        int minutos = calculador.minutos(parametros, paradas, latitud, longitud, ahora);

        assertThat(minutos).isEqualTo(55);
    }

    @Test
    void el_jueves_usa_su_propia_primera_salida_no_la_de_dia_habil() {
        ParametrosDeRuta parametros = parametrosConocidos();
        // Lunes 07:50 → proxima habil 08:00. Jueves 07:50 → proxima jueves 09:00.
        ZonedDateTime jueves = ZonedDateTime.of(
                LocalDate.of(2026, 9, 17), LocalTime.of(7, 50), CalculadorDeTiempoEstimado.ZONA);

        ZonedDateTime salida = calculador.proximaSalidaProgramada(parametros, jueves);

        assertThat(TipoDeDia.de(jueves.toLocalDate())).isEqualTo(TipoDeDia.JUEVES);
        assertThat(salida.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void el_domingo_tiene_jornada_continua_y_sale_a_mediodia() {
        ParametrosDeRuta parametros = parametrosConocidos();
        ZonedDateTime domingo = ZonedDateTime.of(
                LocalDate.of(2026, 9, 13), LocalTime.of(12, 10), CalculadorDeTiempoEstimado.ZONA);

        ZonedDateTime salida = calculador.proximaSalidaProgramada(parametros, domingo);

        assertThat(TipoDeDia.de(domingo.toLocalDate())).isEqualTo(TipoDeDia.DOMINGO);
        assertThat(salida.toLocalTime()).isEqualTo(LocalTime.of(12, 30));
    }

    private static ParametrosDeRuta parametrosConocidos() {
        return new ParametrosDeRuta(
                1L,
                44,
                18,
                15,
                "08:00,10:00",
                "09:00,11:00",
                "08:00,12:30,16:00");
    }

    private static List<PuntoDeParada> paradasConocidas() {
        return List.of(
                new PuntoDeParada(1, 14.634878, -89.981202),
                new PuntoDeParada(2, 14.632450, -89.987308),
                new PuntoDeParada(3, 14.630328, -89.993654),
                new PuntoDeParada(4, 14.628621, -90.000143));
    }
}
