package gt.muni.jalapa.ecoruta.herramientas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static gt.muni.jalapa.ecoruta.herramientas.SimuladorGps.ParadaEnRuta;
import static gt.muni.jalapa.ecoruta.herramientas.SimuladorGps.Punto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Geometria del simulador: haversine, interpolacion, paradas. */
class SimuladorGpsGeometriaTest {

    @Test
    void haversine_de_un_punto_consigo_mismo_es_cero() {
        Punto p = new Punto(14.6349, -89.9844);
        assertThat(SimuladorGps.metros(p, p)).isZero();
    }

    @Test
    void haversine_de_un_grado_en_el_ecuador_es_la_distancia_conocida() {
        // 1 grado de longitud en el ecuador = 2 * pi * R / 360, con R = 6_371_000.
        Punto a = new Punto(0, 0);
        Punto b = new Punto(0, 1);
        assertThat(SimuladorGps.metros(a, b)).isCloseTo(2 * Math.PI * 6_371_000 / 360, within(1.0));
        // Distancia de referencia entre dos puntos de Jalapa (radio 6_371_000).
        assertThat(SimuladorGps.metros(new Punto(14.6349, -89.9844), new Punto(14.6335, -89.9885)))
                .isCloseTo(467.7727482930944, within(1e-9));
    }

    @Test
    void interpola_a_media_distancia_entre_dos_vertices() {
        List<Punto> puntos = List.of(new Punto(14.0, -90.0), new Punto(14.2, -89.8));
        double[] acc = SimuladorGps.acumulado(puntos);
        Punto medio = SimuladorGps.interpolar(puntos, acc, acc[1] / 2);
        assertThat(medio.latitud()).isCloseTo(14.1, within(1e-9));
        assertThat(medio.longitud()).isCloseTo(-89.9, within(1e-9));
    }

    @Test
    void la_parada_exactamente_en_un_vertice_cae_en_ese_metro() {
        List<Punto> puntos = List.of(
                new Punto(14.00, -90.00),
                new Punto(14.01, -90.00),
                new Punto(14.02, -90.00));
        double[] acc = SimuladorGps.acumulado(puntos);
        ParadaEnRuta cruda = new ParadaEnRuta(0, "Mercado", 2, new Punto(14.01, -90.00));
        List<ParadaEnRuta> situadas = SimuladorGps.situarParadas(puntos, acc, List.of(cruda));
        assertThat(situadas).hasSize(1);
        assertThat(situadas.getFirst().metros()).isCloseTo(acc[1], within(1e-6));
        assertThat(situadas.getFirst().nombre()).isEqualTo("Mercado");
    }

    @Test
    void la_parada_del_cierre_de_vuelta_se_detecta_cuando_desde_es_mayor_que_hasta() {
        // Parque Central vive en el metro 0. El ultimo tramo de la vuelta
        // tiene desde > hasta; sin contemplarlo, esa parada no se anunciaria.
        ParadaEnRuta parque = new ParadaEnRuta(0, "Parque Central", 1, new Punto(14.63, -89.98));
        ParadaEnRuta mercado = new ParadaEnRuta(400, "Mercado", 2, new Punto(14.64, -89.97));
        List<ParadaEnRuta> paradas = List.of(parque, mercado);

        assertThat(SimuladorGps.paradaEn(paradas, 480, 20)).isEqualTo(parque);
        assertThat(SimuladorGps.paradaEn(paradas, 0, 20)).isNull();
        assertThat(SimuladorGps.paradaEn(paradas, 350, 410).nombre()).isEqualTo("Mercado");
    }
}
