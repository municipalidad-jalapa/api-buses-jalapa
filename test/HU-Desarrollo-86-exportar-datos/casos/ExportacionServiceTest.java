package gt.muni.jalapa.ecoruta.exportacion.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.exportacion.ExportacionProperties;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas del rango de fechas de la exportacion (HU Desarrollo-86, criterio "rango seleccionable"). */
class ExportacionServiceTest {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private ExportacionRepository repositorio;
    private ExportacionService servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(ExportacionRepository.class);
        when(repositorio.demandaPorDiaYParada(any(), any(), any())).thenReturn(List.of());
        when(repositorio.recorridosPorDiaYBus(any(), any(), any(), anyInt())).thenReturn(List.of());

        servicio = new ExportacionService(
                repositorio,
                new ExportacionProperties("America/Guatemala", 366, 300),
                Clock.fixed(Instant.parse("2026-09-16T15:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void el_rango_es_inclusivo_y_va_de_la_medianoche_de_guatemala_a_la_del_dia_siguiente() {
        servicio.exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        verify(repositorio).demandaPorDiaYParada(desde.capture(), hasta.capture(), eq(GUATEMALA));

        // Guatemala es UTC-6 todo el año: su medianoche es las 06:00 UTC.
        assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-09-01T06:00:00Z"));
        // "hasta 15" incluye el 15 completo: el limite exclusivo es el 16 a medianoche.
        assertThat(hasta.getValue()).isEqualTo(Instant.parse("2026-09-16T06:00:00Z"));
    }

    @Test
    void la_misma_ventana_se_usa_para_demanda_y_recorridos() {
        servicio.exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));

        Instant inicio = Instant.parse("2026-09-01T06:00:00Z");
        Instant fin = Instant.parse("2026-09-16T06:00:00Z");
        verify(repositorio).demandaPorDiaYParada(inicio, fin, GUATEMALA);
        verify(repositorio).recorridosPorDiaYBus(inicio, fin, GUATEMALA, 300);
    }

    @Test
    void un_solo_dia_es_un_rango_valido() {
        var archivo = servicio.exportar(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));

        assertThat(archivo.contenido()).isNotEmpty();
    }

    @Test
    void el_nombre_del_archivo_lleva_el_rango() {
        var archivo = servicio.exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));

        assertThat(archivo.nombre()).isEqualTo("ecoruta-servicio_2026-09-01_a_2026-09-15.xlsx");
    }

    @Test
    void un_rango_invertido_se_rechaza_sin_consultar_la_base() {
        assertThatThrownBy(() -> servicio.exportar(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("desde");

        verify(repositorio, never()).demandaPorDiaYParada(any(), any(), any());
    }

    @Test
    void el_maximo_de_dias_es_inclusivo() {
        // Del 1 de enero de 2025 al 1 de enero de 2026, ambos inclusive, son 366 dias: entra justo.
        servicio.exportar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> servicio.exportar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2))) // 367
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("367")
                .hasMessageContaining("366");
    }

    @Test
    void las_propiedades_por_defecto_son_las_de_guatemala() {
        ExportacionProperties porDefecto = new ExportacionProperties(null, 0, 0);

        assertThat(porDefecto.zona()).isEqualTo(GUATEMALA);
        assertThat(porDefecto.rangoMaximoDias()).isEqualTo(366);
        assertThat(porDefecto.saltoMaximoSegundos()).isEqualTo(300);
    }

    @Test
    void una_zona_horaria_invalida_falla_al_arrancar() {
        assertThatThrownBy(() -> new ExportacionProperties("Marte/Olympus", 366, 300))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Marte/Olympus");
    }
}
