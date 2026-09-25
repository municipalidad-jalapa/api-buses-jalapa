package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.dominio.Reserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.CrearReservaRequest;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ReservaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SCRUM-306: creacion de reserva, sin levantar Spring. */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
    private static final String DISPOSITIVO = "550e8400-e29b-41d4-a716-446655440000";
    private static final int TTL_MINUTOS = 20;
    private static final int GEOCERCA = 150;
    private static final int RITMO_MINIMO_SEGUNDOS = 5;

    @Mock
    private ReservaRepository reservas;

    @Mock
    private ParadaRepository paradas;

    private DemandaProperties demanda;
    private ReservaService servicio;

    @BeforeEach
    void armarServicio() {
        demanda = new DemandaProperties(TTL_MINUTOS, 60, 10, GEOCERCA, RITMO_MINIMO_SEGUNDOS);
        servicio = new ReservaService(reservas, paradas, demanda, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void crea_una_reserva_activa_con_expiracion_segun_ttl() {
        Parada parada = parada(1L);
        when(paradas.findById(1L)).thenReturn(Optional.of(parada));
        when(paradas.estaDentroDeGeocerca(1L, 14.634878, -89.981202, GEOCERCA)).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), anyCollection())).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        ReservaResponse respuesta = servicio.crear(peticionCerca());

        assertThat(respuesta.id()).isEqualTo(42L);
        assertThat(respuesta.paradaId()).isEqualTo(1L);
        assertThat(respuesta.estado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(respuesta.expiraEn()).isEqualTo(AHORA.plusSeconds(TTL_MINUTOS * 60L));

        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).saveAndFlush(captor.capture());
        Reserva guardada = captor.getValue();
        assertThat(guardada.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(guardada.getCreadoEn()).isEqualTo(AHORA);
        assertThat(guardada.getExpiraEn()).isEqualTo(AHORA.plus(demanda.vigencia()));
        assertThat(guardada.getDispositivoId()).isEqualTo(DISPOSITIVO);
    }

    @Test
    void parada_inexistente_no_consulta_geocerca_ni_guarda() {
        when(paradas.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new CrearReservaRequest(
                DISPOSITIVO, 999L, 14.634878, -89.981202)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Parada")
                .hasMessageContaining("999");

        verify(paradas, never()).estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble());
        verifyNoInteractions(reservas);
    }

    @Test
    void dispositivo_lejos_de_la_parada_no_guarda() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(1L, 14.0, -89.0, GEOCERCA)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(new CrearReservaRequest(
                DISPOSITIVO, 1L, 14.0, -89.0)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("acercarte");

        verify(reservas, never()).saveAndFlush(any());
        verify(reservas, never()).existsByDispositivoIdAndEstadoIn(anyString(), anyCollection());
    }

    @Test
    void dispositivo_con_reserva_activa_no_guarda() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), eq(ReservaService.ESTADOS_VIGENTES)))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticionCerca()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya tiene una reserva activa");

        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void dispositivo_con_reserva_renovada_no_guarda() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), eq(ReservaService.ESTADOS_VIGENTES)))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticionCerca()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya tiene una reserva activa");

        verify(reservas, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoReserva.class, names = {"ABORDO", "CANCELADA", "EXPIRADA"})
    void estados_no_vigentes_no_bloquean_una_reserva_nueva(EstadoReserva ignorado) {
        // El parametro documenta el criterio: el servicio consulta solo ACTIVA/RENOVADA.
        assertThat(ReservaService.ESTADOS_VIGENTES).doesNotContain(ignorado);

        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), eq(ReservaService.ESTADOS_VIGENTES)))
                .thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(7L);
            return r;
        });

        ReservaResponse respuesta = servicio.crear(peticionCerca());

        assertThat(respuesta.estado()).isEqualTo(EstadoReserva.ACTIVA);
        verify(reservas).saveAndFlush(any(Reserva.class));
    }

    @Test
    void violacion_concurrente_del_indice_unico_se_traduce_a_regla_de_negocio() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(anyString(), anyCollection())).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate",
                        new RuntimeException(
                                "ERROR: duplicate key value violates unique constraint "
                                        + "\"uq_registro_activo_por_dispositivo\"")));

        assertThatThrownBy(() -> servicio.crear(peticionCerca()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya tiene una reserva activa");
    }

    @Test
    void otra_violacion_de_integridad_no_se_traduce_a_reserva_duplicada() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(anyString(), anyCollection())).thenReturn(false);
        DataIntegrityViolationException otra = new DataIntegrityViolationException(
                "fk",
                new RuntimeException("ERROR: insert or update on table \"registros_espera\" "
                        + "violates foreign key constraint \"registros_espera_parada_id_fkey\""));
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.crear(peticionCerca()))
                .isSameAs(otra);
    }

    @Test
    void el_estado_persistido_siempre_es_activa() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(anyString(), anyCollection())).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        servicio.crear(peticionCerca());

        ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test
    void expira_en_usa_ttl_configurable_y_no_un_numero_hardcodeado() {
        DemandaProperties otroTtl = new DemandaProperties(7, 60, 10, GEOCERCA, RITMO_MINIMO_SEGUNDOS);
        ReservaService conOtroTtl = new ReservaService(
                reservas, paradas, otroTtl, Clock.fixed(AHORA, ZoneOffset.UTC));

        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(anyString(), anyCollection())).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReservaResponse respuesta = conOtroTtl.crear(peticionCerca());

        assertThat(respuesta.expiraEn()).isEqualTo(AHORA.plus(otroTtl.vigencia()));
        assertThat(respuesta.expiraEn()).isNotEqualTo(AHORA.plusSeconds(TTL_MINUTOS * 60L));
    }

    @Test
    void dispositivo_que_creo_una_reserva_hace_poco_no_puede_crear_otra_aunque_no_tenga_vigente() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), eq(ReservaService.ESTADOS_VIGENTES)))
                .thenReturn(false);
        when(reservas.existsByDispositivoIdAndCreadoEnAfter(
                DISPOSITIVO, AHORA.minusSeconds(RITMO_MINIMO_SEGUNDOS)))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticionCerca()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ritmo");

        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void dispositivo_sin_creaciones_recientes_si_puede_crear_otra() {
        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(eq(DISPOSITIVO), eq(ReservaService.ESTADOS_VIGENTES)))
                .thenReturn(false);
        when(reservas.existsByDispositivoIdAndCreadoEnAfter(
                DISPOSITIVO, AHORA.minusSeconds(RITMO_MINIMO_SEGUNDOS)))
                .thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(9L);
            return r;
        });

        ReservaResponse respuesta = servicio.crear(peticionCerca());

        assertThat(respuesta.estado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test
    void el_ritmo_minimo_usa_la_ventana_configurada_y_no_un_numero_hardcodeado() {
        DemandaProperties otroRitmo = new DemandaProperties(TTL_MINUTOS, 60, 10, GEOCERCA, 30);
        ReservaService conOtroRitmo = new ReservaService(
                reservas, paradas, otroRitmo, Clock.fixed(AHORA, ZoneOffset.UTC));

        when(paradas.findById(1L)).thenReturn(Optional.of(parada(1L)));
        when(paradas.estaDentroDeGeocerca(anyLong(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        when(reservas.existsByDispositivoIdAndEstadoIn(anyString(), anyCollection())).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        conOtroRitmo.crear(peticionCerca());

        verify(reservas).existsByDispositivoIdAndCreadoEnAfter(DISPOSITIVO, AHORA.minusSeconds(30));
    }

    private static CrearReservaRequest peticionCerca() {
        return new CrearReservaRequest(DISPOSITIVO, 1L, 14.634878, -89.981202);
    }

    private static Parada parada(Long id) {
        Parada parada = new Parada();
        parada.setId(id);
        parada.setNombre("Parque Central");
        return parada;
    }
}
