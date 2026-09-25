package gt.muni.jalapa.ecoruta.precision.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.precision.dominio.LlegadaReal;
import gt.muni.jalapa.ecoruta.precision.dominio.PrediccionEta;
import gt.muni.jalapa.ecoruta.precision.repositorio.LlegadaRealRepository;
import gt.muni.jalapa.ecoruta.precision.repositorio.ParadasParaPrecisionRepository;
import gt.muni.jalapa.ecoruta.precision.repositorio.PrediccionEtaRepository;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectorDeLlegadaRealTest {

    private static final Instant PREDICHO = Instant.parse("2026-09-13T12:00:00Z");
    private static final Instant LLEGADA = Instant.parse("2026-09-13T12:10:00Z");

    @Mock
    private PrediccionEtaRepository predicciones;
    @Mock
    private LlegadaRealRepository llegadas;
    @Mock
    private ParadasParaPrecisionRepository paradas;
    @Mock
    private VehiculoRepository vehiculos;

    private DetectorDeLlegadaReal detector;
    private Parada parque;
    private Vehiculo bus;

    @BeforeEach
    void armar() {
        detector = new DetectorDeLlegadaReal(
                predicciones, llegadas, paradas, vehiculos,
                new EtaPrecisionProperties(null, 150),
                new CriterioDeEvaluacionEta(new EtaPrecisionProperties(null, 150)));
        Ruta ruta = new Ruta();
        ruta.setId(1L);
        parque = new Parada();
        parque.setId(7L);
        parque.setRuta(ruta);
        parque.setUbicacion(Geo.punto(14.634878, -89.981202));
        bus = new Vehiculo("BUS-01", "P-000BBB");
        bus.setId(1L);
    }

    @Test
    void asocia_la_llegada_a_la_prediccion_vigente_cuando_esta_en_geocerca() {
        when(vehiculos.findByIdentificador("BUS-01")).thenReturn(Optional.of(bus));
        when(paradas.todasConRuta()).thenReturn(List.of(parque));
        PrediccionEta vigente = new PrediccionEta(1L, 7L, 1L, 8, PREDICHO, true);
        when(predicciones.vigente(1L, 7L, 1L, LLEGADA)).thenReturn(Optional.of(vigente));
        when(llegadas.save(any(LlegadaReal.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<LlegadaReal> llegada = detector.detectar(posicion(14.634878, -89.981202, 0.0));

        assertThat(llegada).isPresent();
        assertThat(llegada.get().getPrediccion()).isSameAs(vigente);
        assertThat(llegada.get().getErrorMin()).isCloseTo(2.0, within(0.001));
        ArgumentCaptor<LlegadaReal> captor = ArgumentCaptor.forClass(LlegadaReal.class);
        verify(llegadas).save(captor.capture());
        assertThat(captor.getValue().getLlegadaEn()).isEqualTo(LLEGADA);
    }

    @Test
    void en_ruta_fuera_de_geocerca_no_registra_llegada() {
        when(vehiculos.findByIdentificador("BUS-01")).thenReturn(Optional.of(bus));
        when(paradas.todasConRuta()).thenReturn(List.of(parque));

        Optional<LlegadaReal> llegada = detector.detectar(posicion(14.640000, -89.990000, 18.0));

        assertThat(llegada).isEmpty();
        verify(llegadas, never()).save(any());
    }

    @Test
    void sin_vehiculo_conocido_es_ausencia_y_no_registra() {
        when(vehiculos.findByIdentificador("BUS-99")).thenReturn(Optional.empty());

        Optional<LlegadaReal> llegada = detector.detectar(
                new PosicionActualResponse(14.634878, -89.981202, 0.0, LLEGADA, "BUS-99"));

        assertThat(llegada).isEmpty();
        verify(llegadas, never()).save(any());
    }

    @Test
    void ignora_la_parada_de_otra_ruta_aunque_comparta_coordenadas() {
        bus.setRutaId(1L);
        Ruta otraRuta = new Ruta();
        otraRuta.setId(2L);
        Parada mismaEsquinaOtraRuta = new Parada();
        mismaEsquinaOtraRuta.setId(99L);
        mismaEsquinaOtraRuta.setRuta(otraRuta);
        mismaEsquinaOtraRuta.setUbicacion(Geo.punto(14.634878, -89.981202));
        when(vehiculos.findByIdentificador("BUS-01")).thenReturn(Optional.of(bus));
        when(paradas.todasConRuta()).thenReturn(List.of(mismaEsquinaOtraRuta, parque));
        PrediccionEta vigente = new PrediccionEta(1L, 7L, 1L, 8, PREDICHO, true);
        when(predicciones.vigente(1L, 7L, 1L, LLEGADA)).thenReturn(Optional.of(vigente));
        when(llegadas.save(any(LlegadaReal.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<LlegadaReal> llegada = detector.detectar(posicion(14.634878, -89.981202, 0.0));

        assertThat(llegada).isPresent();
        verify(predicciones).vigente(1L, 7L, 1L, LLEGADA);
        verify(predicciones, never()).vigente(2L, 99L, 1L, LLEGADA);
    }

    private static PosicionActualResponse posicion(double lat, double lon, Double velocidad) {
        return new PosicionActualResponse(lat, lon, velocidad, LLEGADA, "BUS-01");
    }
}
