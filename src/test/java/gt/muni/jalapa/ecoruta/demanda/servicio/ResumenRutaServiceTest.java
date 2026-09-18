package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CatalogoService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ParadaDto;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservasPorParadaDto;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ResumenRutaResponse;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SCRUM-283: orquestacion del resumen de arranque. */
@ExtendWith(MockitoExtension.class)
class ResumenRutaServiceTest {

    @Mock
    private CatalogoService catalogo;

    @Mock
    private TelemetriaService telemetria;

    @Mock
    private DemandaService demanda;

    @InjectMocks
    private ResumenRutaService servicio;

    private RutaResponse rutaEjemplo;
    private PosicionActualResponse posicionEjemplo;

    @BeforeEach
    void preparar() {
        rutaEjemplo = new RutaResponse(
                1L,
                "Ruta de ejemplo",
                true,
                List.of(
                        parada(1L, 1),
                        parada(2L, 2),
                        parada(3L, 3),
                        parada(4L, 4)),
                List.of());
        posicionEjemplo = new PosicionActualResponse(14.63, -89.98, 20.0, Instant.parse("2026-08-17T10:00:00Z"), "BUS-01");
    }

    @Test
    void compone_ruta_posicion_y_reservas() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(Map.of(1L, 2L, 3L, 1L));
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.of(posicionEjemplo));

        ResumenRutaCompuesto resumen = servicio.componer(1L);

        assertThat(resumen.ruta()).isEqualTo(rutaEjemplo);
        assertThat(resumen.posicionActual()).isEqualTo(posicionEjemplo);
        assertThat(resumen.reservasActivasPorParada())
                .containsExactly(
                        Map.entry(1L, 2L),
                        Map.entry(2L, 0L),
                        Map.entry(3L, 1L),
                        Map.entry(4L, 0L));
    }

    @Test
    void tolera_que_el_bus_no_tenga_posicion() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        ResumenRutaCompuesto resumen = servicio.componer(1L);

        assertThat(resumen.ruta()).isEqualTo(rutaEjemplo);
        assertThat(resumen.posicionActual()).isNull();
        assertThat(resumen.reservasActivasPorParada()).containsExactly(
                Map.entry(1L, 0L),
                Map.entry(2L, 0L),
                Map.entry(3L, 0L),
                Map.entry(4L, 0L));
    }

    @Test
    void completa_con_cero_las_paradas_sin_reservas() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(Map.of(1L, 2L, 3L, 1L));
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        ResumenRutaCompuesto resumen = servicio.componer(1L);

        assertThat(resumen.reservasActivasPorParada())
                .containsExactly(
                        Map.entry(1L, 2L),
                        Map.entry(2L, 0L),
                        Map.entry(3L, 1L),
                        Map.entry(4L, 0L));
    }

    @Test
    void propaga_recurso_no_encontrado_si_la_ruta_no_existe() {
        when(catalogo.buscar(999L)).thenThrow(new RecursoNoEncontradoException("Ruta", 999L));

        assertThatThrownBy(() -> servicio.componer(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(demanda);
        verifyNoInteractions(telemetria);
    }

    @Test
    void consulta_demanda_una_sola_vez_para_todas_las_paradas() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        servicio.componer(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(demanda).contarReservasActivasPorParada(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void consulta_telemetria_una_sola_vez() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        servicio.componer(1L);

        // La posicion es la del bus de la ruta pedida, no la ultima de la flota.
        verify(telemetria).posicionVigentePorRuta(1L);
    }

    // --- resumir(): el mismo dato ya mapeado al DTO del contrato REST (SCRUM-284) ---

    @Test
    void resumir_mapea_la_ruta_con_sus_paradas_en_orden() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        ResumenRutaResponse dto = servicio.resumir(1L);

        assertThat(dto.ruta().id()).isEqualTo(1L);
        assertThat(dto.ruta().nombre()).isEqualTo("Ruta de ejemplo");
        assertThat(dto.ruta().paradas()).extracting(ParadaDto::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(dto.ruta().paradas()).extracting(ParadaDto::orden).containsExactly(1, 2, 3, 4);
        assertThat(dto.ruta().trazado()).isEmpty();
    }

    @Test
    void resumir_incluye_la_posicion_cuando_el_bus_ya_reporto() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.of(posicionEjemplo));

        ResumenRutaResponse dto = servicio.resumir(1L);

        assertThat(dto.posicionActual()).isNotNull();
        assertThat(dto.posicionActual().latitud()).isEqualTo(14.63);
        assertThat(dto.posicionActual().longitud()).isEqualTo(-89.98);
        assertThat(dto.posicionActual().velocidadKmh()).isEqualTo(20.0);
        assertThat(dto.posicionActual().capturadoEn()).isEqualTo(Instant.parse("2026-08-17T10:00:00Z"));
    }

    @Test
    void resumir_entrega_la_respuesta_aunque_el_bus_no_haya_reportado_posicion() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(any())).thenReturn(Map.of());
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        ResumenRutaResponse dto = servicio.resumir(1L);

        assertThat(dto.ruta()).isNotNull();
        assertThat(dto.posicionActual()).isNull();
        assertThat(dto.reservasActivas()).isNotNull();
    }

    @Test
    void resumir_lista_todas_las_paradas_con_su_conteo_incluidos_los_ceros() {
        when(catalogo.buscar(1L)).thenReturn(rutaEjemplo);
        when(demanda.contarReservasActivasPorParada(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(Map.of(1L, 2L, 3L, 1L));
        when(telemetria.posicionVigentePorRuta(anyLong())).thenReturn(Optional.empty());

        ResumenRutaResponse dto = servicio.resumir(1L);

        assertThat(dto.reservasActivas().porParada()).containsExactly(
                new ReservasPorParadaDto(1L, 2L),
                new ReservasPorParadaDto(2L, 0L),
                new ReservasPorParadaDto(3L, 1L),
                new ReservasPorParadaDto(4L, 0L));
        assertThat(dto.reservasActivas().calculadoEn()).isNotNull();
    }

    @Test
    void resumir_propaga_recurso_no_encontrado_si_la_ruta_no_existe() {
        when(catalogo.buscar(999L)).thenThrow(new RecursoNoEncontradoException("Ruta", 999L));

        assertThatThrownBy(() -> servicio.resumir(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(demanda);
        verifyNoInteractions(telemetria);
    }

    private static ParadaResponse parada(long id, int orden) {
        return new ParadaResponse(id, "Parada " + id, 14.63, -89.98, orden);
    }
}
