package gt.muni.jalapa.ecoruta.calles;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.PuntoResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El trazo a mano se muestrea parejo antes de engancharlo a las calles. */
class MuestreoDelTrazoTest {

    @Test
    void toma_un_punto_cada_35_metros_y_conserva_los_extremos() {
        // ~221 m hacia el norte.
        PuntoResponse a = new PuntoResponse(14.6300, -89.9850);
        PuntoResponse b = new PuntoResponse(14.6320, -89.9850);

        List<PuntoResponse> muestras = RedDeCalles.muestrear(List.of(a, b));

        assertThat(muestras.get(0)).isEqualTo(a);
        assertThat(muestras.get(muestras.size() - 1)).isEqualTo(b);
        // 221 m / 35 m = 6 intermedias, mas los dos extremos.
        assertThat(muestras).hasSize(8);
    }

    @Test
    void un_trazo_muy_largo_no_pasa_de_unas_400_muestras() {
        PuntoResponse a = new PuntoResponse(14.60, -90.02);
        PuntoResponse b = new PuntoResponse(14.68, -89.95);

        assertThat(RedDeCalles.muestrear(List.of(a, b)).size()).isLessThanOrEqualTo(402);
    }
}
