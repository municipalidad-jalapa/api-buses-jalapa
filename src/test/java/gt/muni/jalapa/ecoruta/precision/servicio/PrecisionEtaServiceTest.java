package gt.muni.jalapa.ecoruta.precision.servicio;

import gt.muni.jalapa.ecoruta.precision.dominio.LlegadaReal;
import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import gt.muni.jalapa.ecoruta.precision.repositorio.LlegadaRealRepository;
import gt.muni.jalapa.ecoruta.precision.web.dto.PrecisionEtaResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrecisionEtaServiceTest {

    private static final Instant DESDE = Instant.parse("2026-09-13T00:00:00Z");
    private static final Instant HASTA = Instant.parse("2026-09-13T23:59:59Z");

    @Mock
    private LlegadaRealRepository llegadas;

    @Test
    void agrega_promedio_maximo_por_parada_y_por_franja() {
        PrecisionEtaService servicio = new PrecisionEtaService(llegadas);
        // 06:30 Guatemala = 12:30 UTC. Franja 06:00-09:00.
        Instant manana = LocalDateTime.of(2026, 9, 13, 6, 30).atZone(ZoneId.of("America/Guatemala")).toInstant();
        Instant tambienManana = LocalDateTime.of(2026, 9, 13, 7, 0).atZone(ZoneId.of("America/Guatemala")).toInstant();
        when(llegadas.deLaRutaEntre(1L, DESDE, HASTA)).thenReturn(List.of(
                llegada(1L, 7L, 1.8, manana),
                llegada(1L, 7L, 3.0, tambienManana),
                llegada(1L, 8L, 9.0, manana)));

        PrecisionEtaResponse r = servicio.deLaRuta(1L, DESDE, HASTA);

        assertThat(r.rutaId()).isEqualTo(1L);
        assertThat(r.errorPromedioMin()).isEqualTo(4.6);
        assertThat(r.errorMaximoMin()).isEqualTo(9.0);
        assertThat(r.porParada()).hasSize(2);
        assertThat(r.porParada().get(0).paradaId()).isEqualTo(7L);
        assertThat(r.porParada().get(0).errorPromedioMin()).isEqualTo(2.4);
        assertThat(r.porParada().get(0).muestras()).isEqualTo(2);
        assertThat(r.porFranja()).extracting(f -> f.franja()).containsExactly("06:00-09:00");
        assertThat(r.porFranja().getFirst().errorPromedioMin()).isEqualTo(4.6);
    }

    @Test
    void no_mezcla_errores_de_otra_ruta() {
        PrecisionEtaService servicio = new PrecisionEtaService(llegadas);
        when(llegadas.deLaRutaEntre(1L, DESDE, HASTA)).thenReturn(List.of(
                llegada(1L, 7L, 2.0, Instant.parse("2026-09-13T14:00:00Z"))));
        when(llegadas.deLaRutaEntre(2L, DESDE, HASTA)).thenReturn(List.of(
                llegada(2L, 20L, 40.0, Instant.parse("2026-09-13T14:00:00Z"))));

        PrecisionEtaResponse ruta1 = servicio.deLaRuta(1L, DESDE, HASTA);
        PrecisionEtaResponse ruta2 = servicio.deLaRuta(2L, DESDE, HASTA);

        assertThat(ruta1.errorPromedioMin()).isEqualTo(2.0);
        assertThat(ruta1.errorMaximoMin()).isEqualTo(2.0);
        assertThat(ruta1.porParada()).extracting(p -> p.paradaId()).containsExactly(7L);
        assertThat(ruta2.errorPromedioMin()).isEqualTo(40.0);
        assertThat(ruta2.porParada()).extracting(p -> p.paradaId()).containsExactly(20L);
    }

    private static LlegadaReal llegada(Long rutaId, Long paradaId, double error, Instant cuando) {
        PrediccionEta prediccion = new PrediccionEta(rutaId, paradaId, 1L, 8, cuando.minusSeconds(600), true);
        return new LlegadaReal(prediccion, cuando, error);
    }
}
