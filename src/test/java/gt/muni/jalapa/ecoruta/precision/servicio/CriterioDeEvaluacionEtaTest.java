package gt.muni.jalapa.ecoruta.precision.servicio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CriterioDeEvaluacionEtaTest {

    @Test
    void sin_posicion_es_ausencia_de_datos_y_no_registra_llegada() {
        CriterioDeEvaluacionEta criterio = new CriterioDeEvaluacionEta(sinMargen());

        CasoOperativoEta caso = criterio.clasificar(false, false);

        assertThat(caso).isEqualTo(CasoOperativoEta.SIN_DATOS_RECIENTES);
        assertThat(criterio.registraLlegada(caso)).isFalse();
    }

    @Test
    void fuera_de_geocerca_es_bus_en_ruta_y_no_registra_llegada() {
        CriterioDeEvaluacionEta criterio = new CriterioDeEvaluacionEta(sinMargen());

        CasoOperativoEta caso = criterio.clasificar(true, false);

        assertThat(caso).isEqualTo(CasoOperativoEta.BUS_EN_RUTA);
        assertThat(criterio.registraLlegada(caso)).isFalse();
    }

    @Test
    void dentro_de_geocerca_es_bus_detenido_y_registra_llegada() {
        CriterioDeEvaluacionEta criterio = new CriterioDeEvaluacionEta(sinMargen());

        CasoOperativoEta caso = criterio.clasificar(true, true);

        assertThat(caso).isEqualTo(CasoOperativoEta.BUS_DETENIDO);
        assertThat(criterio.registraLlegada(caso)).isTrue();
    }

    @Test
    void sin_margen_confirmado_no_acepta_ni_rechaza() {
        CriterioDeEvaluacionEta criterio = new CriterioDeEvaluacionEta(sinMargen());

        assertThat(criterio.acepta(1.0)).isEmpty();
    }

    @Test
    void con_margen_confirmado_compara_el_error() {
        CriterioDeEvaluacionEta criterio = new CriterioDeEvaluacionEta(new EtaPrecisionProperties(5, 150));

        assertThat(criterio.acepta(5.0)).contains(true);
        assertThat(criterio.acepta(5.1)).contains(false);
    }

    private static EtaPrecisionProperties sinMargen() {
        return new EtaPrecisionProperties(null, 150);
    }
}
