package gt.muni.jalapa.ecoruta.panel.servicio;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CatalogoService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.demanda.servicio.DemandaService;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.panel.web.dto.PanelRutaDto;
import gt.muni.jalapa.ecoruta.panel.web.dto.PanelRutasResponse;
import gt.muni.jalapa.ecoruta.panel.web.dto.ReservaPorParadaDto;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-79: orquestacion del tablero municipal para todas las rutas activas. */
@ExtendWith(MockitoExtension.class)
class PanelServiceTest {

    @Mock
    private CatalogoService catalogo;

    @Mock
    private VehiculoRepository vehiculos;

    @Mock
    private TelemetriaService telemetria;

    @Mock
    private DemandaService demanda;

    private PanelService servicio;

    private RutaResponse rutaUno;
    private RutaResponse rutaDos;
    private Vehiculo busUno;

    @BeforeEach
    void preparar() {
        servicio = new PanelService(catalogo, vehiculos, telemetria, demanda, new PanelProperties(5));
        rutaUno = ruta(1L, "Ruta Centro", List.of(parada(1L), parada(2L)));
        rutaDos = ruta(2L, "Ruta Metroplaza", List.of(parada(10L)));
        busUno = new Vehiculo("BUS-01", "P-000BBB");
        busUno.setId(3L);
    }

    @Test
    void lista_cada_ruta_activa_con_su_bus_y_reservas() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno, rutaDos));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.of(busUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(2L)).thenReturn(Optional.empty());
        when(telemetria.posicionVigente(3L)).thenReturn(Optional.of(posicionReciente()));
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of(1L, 4L));

        PanelRutasResponse respuesta = servicio.listar();

        assertThat(respuesta.rutas()).hasSize(2);
        PanelRutaDto conBus = respuesta.rutas().get(0);
        assertThat(conBus.rutaId()).isEqualTo(1L);
        assertThat(conBus.nombre()).isEqualTo("Ruta Centro");
        assertThat(conBus.vehiculoId()).isEqualTo(3L);
        assertThat(conBus.posicion()).isNotNull();
        assertThat(conBus.posicion().latitud()).isEqualTo(14.6335);
        assertThat(conBus.posicion().longitud()).isEqualTo(-89.9885);
        assertThat(conBus.transmitiendo()).isTrue();
        assertThat(conBus.reservasPorParada()).containsExactly(
                new ReservaPorParadaDto(1L, 4L),
                new ReservaPorParadaDto(2L, 0L));

        PanelRutaDto sinBus = respuesta.rutas().get(1);
        assertThat(sinBus.vehiculoId()).isNull();
        assertThat(sinBus.posicion()).isNull();
        assertThat(sinBus.transmitiendo()).isFalse();

        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(1L);
        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(2L);
        verify(telemetria, times(1)).posicionVigente(3L);
        verify(telemetria, never()).posicionVigentePorRuta(anyLong());
    }

    @Test
    void vehiculo_y_posicion_quedan_null_si_la_ruta_no_tiene_bus() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.empty());
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        PanelRutaDto ruta = servicio.listar().rutas().getFirst();

        assertThat(ruta.vehiculoId()).isNull();
        assertThat(ruta.posicion()).isNull();
        assertThat(ruta.transmitiendo()).isFalse();
        verify(telemetria, never()).posicionVigente(anyLong());
        verify(telemetria, never()).posicionVigentePorRuta(anyLong());
        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(1L);
    }

    @Test
    void posicion_null_y_sin_transmitir_si_el_bus_aun_no_reporta() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.of(busUno));
        when(telemetria.posicionVigente(3L)).thenReturn(Optional.empty());
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        PanelRutaDto ruta = servicio.listar().rutas().getFirst();

        assertThat(ruta.vehiculoId()).isEqualTo(3L);
        assertThat(ruta.posicion()).isNull();
        assertThat(ruta.transmitiendo()).isFalse();
        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(1L);
        verify(telemetria, times(1)).posicionVigente(3L);
        verify(telemetria, never()).posicionVigentePorRuta(anyLong());
    }

    @Test
    void transmite_cuando_la_posicion_es_reciente() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.of(busUno));
        when(telemetria.posicionVigente(3L)).thenReturn(Optional.of(posicionReciente()));
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        PanelRutaDto ruta = servicio.listar().rutas().getFirst();

        assertThat(ruta.vehiculoId()).isEqualTo(3L);
        assertThat(ruta.posicion()).isNotNull();
        assertThat(ruta.transmitiendo()).isTrue();
        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(1L);
        verify(telemetria, times(1)).posicionVigente(3L);
        verify(telemetria, never()).posicionVigentePorRuta(anyLong());
    }

    @Test
    void no_transmite_cuando_la_posicion_es_mas_vieja_que_el_umbral() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.of(busUno));
        when(telemetria.posicionVigente(3L)).thenReturn(Optional.of(posicionVieja()));
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        PanelRutaDto ruta = servicio.listar().rutas().getFirst();

        assertThat(ruta.posicion()).isNotNull();
        assertThat(ruta.transmitiendo()).isFalse();
        verify(vehiculos, times(1)).findFirstByRutaIdAndActivoTrue(1L);
        verify(telemetria, times(1)).posicionVigente(3L);
        verify(telemetria, never()).posicionVigentePorRuta(anyLong());
    }

    @Test
    void reservas_salen_en_cero_cuando_no_hay_ninguna() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(1L)).thenReturn(Optional.empty());
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        assertThat(servicio.listar().rutas().getFirst().reservasPorParada())
                .containsExactly(
                        new ReservaPorParadaDto(1L, 0L),
                        new ReservaPorParadaDto(2L, 0L));
    }

    @Test
    void consulta_demanda_una_sola_vez_para_todas_las_paradas() {
        when(catalogo.listarActivas()).thenReturn(List.of(rutaUno, rutaDos));
        when(vehiculos.findFirstByRutaIdAndActivoTrue(anyLong())).thenReturn(Optional.empty());
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());

        servicio.listar();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(demanda).contarReservasActivasPorParada(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L, 10L);
    }

    private static PosicionActualResponse posicionReciente() {
        return new PosicionActualResponse(
                14.6335, -89.9885, 18.0, Instant.now().minus(Duration.ofMinutes(1)), "BUS-01");
    }

    private static PosicionActualResponse posicionVieja() {
        return new PosicionActualResponse(
                14.6335, -89.9885, 12.0, Instant.now().minus(Duration.ofMinutes(10)), "BUS-01");
    }

    private static RutaResponse ruta(long id, String nombre, List<ParadaResponse> paradas) {
        return new RutaResponse(id, nombre, true, paradas, List.of());
    }

    private static ParadaResponse parada(long id) {
        return new ParadaResponse(id, "Parada " + id, 14.63, -89.98, (int) id);
    }
}
