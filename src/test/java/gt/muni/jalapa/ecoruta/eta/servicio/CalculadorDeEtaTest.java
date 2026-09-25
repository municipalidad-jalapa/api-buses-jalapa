package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.atrasos.servicio.AvisosDeAtraso;
import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.EtaCalculado;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.Lectura;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.ParadaEnTrazado;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.Reincorporacion;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.Trazado;
import gt.muni.jalapa.ecoruta.eta.web.dto.EstadoDelBus;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaParadaResponse;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-166: nucleo del calculo y limite de frecuencia, sin base de datos. */
class CalculadorDeEtaTest {

    /** Sin espera en paradas, para aislar el tiempo de viaje. */
    private static final EtaProperties SIN_ESPERA =
            new EtaProperties(20, 5, 5, 3, 120, 10, 0, 0, 40, 5, 60, 1.3);
    private static final EtaProperties PROPIEDADES = new EtaProperties(20, 5, 5, 3, 120, 10, 5, 20, 40, 5, 60, 1.3);
    private static final Instant T0 = Instant.parse("2026-09-14T15:30:00Z");

    // --- distancia sobre el trazado -----------------------------------------

    @Test
    void los_minutos_salen_de_la_distancia_sobre_el_trazado_y_la_velocidad() {
        // 6000 m de trazado abierto, bus en el 10 %. A 30 km/h = 500 m/min.
        Trazado trazado = new Trazado(0.10, 6000, false);
        List<ParadaEnTrazado> paradas = List.of(
                new ParadaEnTrazado(1L, 1, 0.00),   // rebasada: se omite
                new ParadaEnTrazado(2L, 2, 0.50),   // 2400 m -> 4.8 -> 5 min
                new ParadaEnTrazado(3L, 3, 1.00));  // 5400 m -> 10.8 -> 11 min

        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(paradas, trazado, null, 30, true, 0, SIN_ESPERA);

        assertThat(etas).containsExactly(
                new EtaParadaResponse(2L, 2, 5, true),
                new EtaParadaResponse(3L, 3, 11, true));
    }

    @Test
    void en_un_circuito_la_parada_rebasada_es_la_de_la_vuelta_siguiente() {
        Trazado circuito = new Trazado(0.50, 5000, true);

        // Rebasada por 500 m: le falta el resto de la vuelta, 4500 m.
        assertThat(CalculadorDeEta.metrosPendientes(0.40, circuito)).isCloseTo(4500d, within(1d));
    }

    @Test
    void una_parada_recien_alcanzada_por_ruido_del_gps_no_salta_a_la_vuelta_siguiente() {
        Trazado circuito = new Trazado(0.5002, 50_000, true);   // 10 m despues de la parada

        assertThat(CalculadorDeEta.metrosPendientes(0.5, circuito)).isZero();
    }

    @Test
    void con_velocidad_de_respaldo_el_resultado_no_es_confiable() {
        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(
                List.of(new ParadaEnTrazado(1L, 1, 1.0)), new Trazado(0, 1000, false), null, 20, false, 0,
                SIN_ESPERA);

        assertThat(etas).singleElement()
                .satisfies(eta -> {
                    assertThat(eta.minutos()).isEqualTo(3);   // 1000 m a 333 m/min
                    assertThat(eta.confiable()).isFalse();
                });
    }

    // --- mejora 2: velocidad deducida del GPS -------------------------------

    @Test
    void sin_velocidad_reportada_se_deduce_de_posiciones_seguidas() {
        // 0.001 grados de latitud son ~111 m; en 10 s son ~40 km/h.
        List<Lectura> lecturas = List.of(
                new Lectura(14.001, -90, null, T0.plusSeconds(10)),
                new Lectura(14.000, -90, null, T0));

        List<Double> velocidades = CalculadorDeEta.velocidadesEfectivas(lecturas);

        assertThat(velocidades.get(0)).isCloseTo(40.0, within(0.5));
        assertThat(velocidades.get(1)).isNull();   // la mas vieja no tiene con que compararse
    }

    @Test
    void la_velocidad_reportada_manda_sobre_la_deducida() {
        List<Lectura> lecturas = List.of(
                new Lectura(14.001, -90, 18d, T0.plusSeconds(10)),
                new Lectura(14.000, -90, null, T0));

        assertThat(CalculadorDeEta.velocidadesEfectivas(lecturas).get(0)).isEqualTo(18d);
    }

    @Test
    void un_salto_del_gps_no_se_toma_como_velocidad() {
        // 1.1 km en 5 s serian ~800 km/h.
        List<Lectura> lecturas = List.of(
                new Lectura(14.010, -90, null, T0.plusSeconds(5)),
                new Lectura(14.000, -90, null, T0));

        assertThat(CalculadorDeEta.velocidadesEfectivas(lecturas).get(0)).isNull();
    }

    // --- mejora 3: espera en paradas ----------------------------------------

    @Test
    void cada_parada_intermedia_suma_su_espera_y_mas_si_tiene_reservas() {
        // 36 km/h = 10 m/s. Paradas intermedias a 300 m y 600 m, destino a 1200 m.
        Trazado trazado = new Trazado(0, 1200, false);
        List<ParadaEnTrazado> paradas = List.of(
                new ParadaEnTrazado(1L, 1, 0.25, 0, 0, 0),    // sin reservas: +5 s
                new ParadaEnTrazado(2L, 2, 0.50, 0, 0, 3),    // con reservas: +20 s
                new ParadaEnTrazado(3L, 3, 1.00, 0, 0, 0));

        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(paradas, trazado, null, 36, true, 0, PROPIEDADES);

        // 120 s de viaje + 5 + 20 = 145 s -> 3 min. Sin esperas serian 2.
        assertThat(etas.get(2).minutos()).isEqualTo(3);
        // A la parada 2 solo cuenta la espera de la 1: 60 + 5 = 65 s -> 2 min.
        assertThat(etas.get(1).minutos()).isEqualTo(2);
    }

    @Test
    void detenido_en_una_parada_suma_lo_que_le_falta_de_espera() {
        Trazado trazado = new Trazado(0, 600, false);
        List<ParadaEnTrazado> paradas = List.of(new ParadaEnTrazado(2L, 2, 1.0));

        // 600 m a 36 km/h = 60 s; +50 s que le faltan en la parada = 110 s -> 2 min.
        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(paradas, trazado, null, 36, true, 50, PROPIEDADES);

        assertThat(etas.getFirst().minutos()).isEqualTo(2);
    }

    // --- mejora 4: detenido fuera de parada ----------------------------------

    @Test
    void el_tiempo_detenido_cuenta_desde_la_primera_lectura_lenta_de_la_racha() {
        List<Lectura> lecturas = List.of(
                new Lectura(14, -90, 0d, T0.plusSeconds(400)),
                new Lectura(14, -90, 1d, T0.plusSeconds(200)),
                new Lectura(14, -90, 0d, T0.plusSeconds(100)),
                new Lectura(14, -90, 30d, T0));   // aqui todavia avanzaba

        Duration detenido = CalculadorDeEta.tiempoDetenido(
                lecturas, CalculadorDeEta.velocidadesEfectivas(lecturas), 5);

        assertThat(detenido).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void en_movimiento_no_esta_detenido() {
        List<Lectura> lecturas = List.of(new Lectura(14, -90, 25d, T0));

        assertThat(CalculadorDeEta.tiempoDetenido(lecturas, CalculadorDeEta.velocidadesEfectivas(lecturas), 5))
                .isZero();
    }

    @Test
    void la_parada_cercana_respeta_el_radio() {
        List<ParadaEnTrazado> paradas = List.of(new ParadaEnTrazado(9L, 1, 0, 14.0, -90.0, 0));

        // ~33 m al norte: dentro de 40 m. ~111 m: fuera.
        assertThat(CalculadorDeEta.paradaCercana(paradas, new Lectura(14.0003, -90, 0d, T0), 40)).isPresent();
        assertThat(CalculadorDeEta.paradaCercana(paradas, new Lectura(14.001, -90, 0d, T0), 40)).isEmpty();
    }

    // --- desvio ------------------------------------------------------------

    @Test
    void en_desvio_la_distancia_pasa_por_el_punto_de_reincorporacion() {
        // Trazado abierto de 10 km. Salio en el 20 %, vuelve en el 30 % a 400 m por calles.
        Trazado trazado = new Trazado(0.20, 10_000, false, 250);
        Reincorporacion desvio = new Reincorporacion(0.20, 0.30, 400);

        assertThat(CalculadorDeEta.metrosConDesvio(0.50, trazado, desvio)).isCloseTo(2400d, within(1d));
        assertThat(CalculadorDeEta.metrosConDesvio(0.25, trazado, desvio)).isNaN();   // la salta
        assertThat(CalculadorDeEta.metrosConDesvio(0.10, trazado, desvio)).isNull();  // ya pasada
    }

    @Test
    void en_un_circuito_la_parada_saltada_por_el_desvio_queda_para_la_vuelta_siguiente() {
        Trazado circuito = new Trazado(0.20, 10_000, true, 250);
        Reincorporacion desvio = new Reincorporacion(0.20, 0.30, 400);

        // 400 m para volver + 9500 m de vuelta (del 30 % al 25 % de la siguiente).
        assertThat(CalculadorDeEta.metrosConDesvio(0.25, circuito, desvio)).isCloseTo(9900d, within(1d));
    }

    @Test
    void la_parada_saltada_por_el_desvio_sale_sin_estimacion() {
        Trazado trazado = new Trazado(0.20, 10_000, false, 250);
        Reincorporacion desvio = new Reincorporacion(0.20, 0.30, 400);
        List<ParadaEnTrazado> paradas = List.of(
                new ParadaEnTrazado(1L, 1, 0.25),
                new ParadaEnTrazado(2L, 2, 0.50));

        List<EtaParadaResponse> etas = CalculadorDeEta.estimar(paradas, trazado, desvio, 36, false, 0, SIN_ESPERA);

        assertThat(etas.get(0)).isEqualTo(EtaParadaResponse.noDisponible(1L, 1));
        assertThat(etas.get(1).minutos()).isEqualTo(4);   // 2400 m a 10 m/s = 240 s
    }

    // --- antiguedad y limite de frecuencia -----------------------------------

    @Test
    void una_posicion_mas_vieja_que_el_umbral_se_considera_vieja() {
        CalculadorDeEta calculador = new CalculadorDeEta(null, null, null, PROPIEDADES, null, null,
                Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(calculador.esVieja(T0.minusSeconds(120), T0)).isFalse();
        assertThat(calculador.esVieja(T0.minusSeconds(121), T0)).isTrue();
    }

    @Test
    void no_recalcula_antes_del_intervalo_minimo() {
        CalculadorDeEta calculador = mock(CalculadorDeEta.class);
        when(calculador.calcular(anyLong())).thenReturn(new EtaCalculado(
                new EtaRutaResponse(1L, 1L, T0, EstadoDelBus.EN_RUTA, null, List.of()), T0));
        EtaService servicio = new EtaService(calculador, sinAtrasos(), PROPIEDADES,
                Clock.fixed(T0, ZoneOffset.UTC));

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
        EtaRutaResponse fresca = new EtaRutaResponse(1L, 1L, T0, EstadoDelBus.EN_RUTA, null,
                List.of(new EtaParadaResponse(7L, 3, 6, true)));
        when(calculador.calcular(1L)).thenReturn(new EtaCalculado(fresca, T0));
        when(calculador.esVieja(T0, T0.plusSeconds(300))).thenReturn(true);
        EtaService servicio = new EtaService(calculador, sinAtrasos(), PROPIEDADES,
                Clock.fixed(T0.plusSeconds(300), ZoneOffset.UTC));

        EtaRutaResponse respuesta = servicio.consultar(1L);

        assertThat(respuesta.estado()).isEqualTo(EstadoDelBus.SIN_DATOS);
        assertThat(respuesta.paradas()).singleElement()
                .isEqualTo(EtaParadaResponse.noDisponible(7L, 3));
    }
    /** Sin aviso del piloto: el ETA sale tal cual lo calculo (SCRUM-26, bloque E). */
    private static AvisosDeAtraso sinAtrasos() {
        AvisosDeAtraso atrasos = mock(AvisosDeAtraso.class);
        when(atrasos.vigente(anyLong())).thenReturn(java.util.Optional.empty());
        return atrasos;
    }

}
