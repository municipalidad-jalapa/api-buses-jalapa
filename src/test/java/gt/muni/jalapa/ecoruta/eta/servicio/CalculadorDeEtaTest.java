package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.EtaCalculado;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.ParadaEnTrazado;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.Trazado;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaParadaResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-166: nucleo del calculo y limite de frecuencia, sin base de datos. */
class CalculadorDeEtaTest {

    private static final EtaProperties PROPIEDADES = new EtaProperties(20, 5, 5, 3, 120, 10);
    private static final Instant T0 = Instant.parse("2026-09-14T15:30:00Z");

    @Test
    void los_minutos_salen_de_la_distancia_sobre_el_trazado_y_la_velocidad() {
        // 6000 m de trazado abierto, bus en el 10 %. A 30 km/h = 500 m/min.
        Trazado trazado = new Trazado(0.10, 6000, false);
        List<ParadaEnTrazado> paradas = List.of(
                new ParadaEnTrazado(1L, 1, 0.00),   // rebasada: se omite
                new ParadaEnTrazado(2L, 2, 0.50),   // 2400 m -> 4.8 -> 5 min
                new ParadaEnTrazado(3L, 3, 1.00));  // 5400 m -> 10.8 -> 11 min

        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(paradas, trazado, 30, true);

        assertThat(etas).containsExactly(
                new EtaParadaResponse(2L, 2, 5, true),
                new EtaParadaResponse(3L, 3, 11, true));
    }

    @Test
    void en_un_circuito_la_parada_rebasada_es_la_de_la_vuelta_siguiente() {
        Trazado circuito = new Trazado(0.50, 5000, true);

        // Rebasada por 500 m: le falta el resto de la vuelta, 4500 m.
        assertThat(CalculadorDeEta.metrosPendientes(0.40, circuito)).isEqualTo(4500d, withinMetro());
    }

    @Test
    void una_parada_recien_alcanzada_por_ruido_del_gps_no_salta_a_la_vuelta_siguiente() {
        Trazado circuito = new Trazado(0.5002, 50_000, true);   // 10 m despues de la parada

        assertThat(CalculadorDeEta.metrosPendientes(0.5, circuito)).isZero();
    }

    @Test
    void con_velocidad_de_respaldo_el_resultado_no_es_confiable() {
        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(
                List.of(new ParadaEnTrazado(1L, 1, 1.0)), new Trazado(0, 1000, false), 20, false);

        assertThat(etas).singleElement()
                .satisfies(eta -> {
                    assertThat(eta.minutos()).isEqualTo(3);   // 1000 m a 333 m/min
                    assertThat(eta.confiable()).isFalse();
                });
    }

    @Test
    void una_posicion_mas_vieja_que_el_umbral_se_considera_vieja() {
        CalculadorDeEta calculador = new CalculadorDeEta(null, null, null, PROPIEDADES,
                Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(calculador.esVieja(T0.minusSeconds(120), T0)).isFalse();
        assertThat(calculador.esVieja(T0.minusSeconds(121), T0)).isTrue();
    }

    @Test
    void no_recalcula_antes_del_intervalo_minimo() {
        CalculadorDeEta calculador = mock(CalculadorDeEta.class);
        when(calculador.calcular(anyLong())).thenReturn(new EtaCalculado(
                new EtaRutaResponse(1L, 1L, T0, List.of()), T0));
        EtaService servicio = new EtaService(calculador, PROPIEDADES, Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(servicio.recalcularSiCorresponde(1L, T0)).isTrue();
        assertThat(servicio.recalcularSiCorresponde(1L, T0.plusSeconds(9))).isFalse();
        assertThat(servicio.recalcularSiCorresponde(1L, T0.plusSeconds(10))).isTrue();
        // Cada ruta lleva su propio limite.
        assertThat(servicio.recalcularSiCorresponde(2L, T0.plusSeconds(10))).isTrue();

        verify(calculador, times(2)).calcular(1L);
    }

    @Test
    void la_cache_entrega_sin_estimacion_cuando_la_posicion_envejecio() {
        CalculadorDeEta calculador = mock(CalculadorDeEta.class);
        EtaRutaResponse fresca = new EtaRutaResponse(1L, 1L, T0,
                List.of(new EtaParadaResponse(7L, 3, 6, true)));
        when(calculador.calcular(1L)).thenReturn(new EtaCalculado(fresca, T0));
        when(calculador.esVieja(T0, T0.plusSeconds(300))).thenReturn(true);
        EtaService servicio = new EtaService(calculador, PROPIEDADES,
                Clock.fixed(T0.plusSeconds(300), ZoneOffset.UTC));

        EtaRutaResponse respuesta = servicio.consultar(1L);

        assertThat(respuesta.paradas()).singleElement()
                .isEqualTo(EtaParadaResponse.noDisponible(7L, 3));
    }

    private static org.assertj.core.data.Offset<Double> withinMetro() {
        return org.assertj.core.data.Offset.offset(1d);
    }
}
