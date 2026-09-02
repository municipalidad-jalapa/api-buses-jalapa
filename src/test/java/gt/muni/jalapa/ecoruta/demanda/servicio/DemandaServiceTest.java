package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoRegistroDemanda;
import gt.muni.jalapa.ecoruta.demanda.dominio.RegistroDemanda;
import gt.muni.jalapa.ecoruta.demanda.repositorio.DemandaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-289: cancelacion de registros y validacion de pertenencia.
 */
@ExtendWith(MockitoExtension.class)
class DemandaServiceTest {

    @Mock
    private DemandaRepository demandaRepository;

    @InjectMocks
    private DemandaService demandaService;

    @Test
    void cancelar_un_registro_activo_lo_marca_como_cancelado() {

        RegistroDemanda registro = registroActivo("dispositivo-uno");

        when(demandaRepository.findById(1L))
                .thenReturn(Optional.of(registro));

        demandaService.cancelarRegistro(1L, "dispositivo-uno");

        assertThat(registro.getEstado())
                .isEqualTo(EstadoRegistroDemanda.CANCELADO);

        assertThat(registro.getCanceladoEn())
                .isNotNull();

        verify(demandaRepository).save(registro);
    }

    @Test
    void no_permite_cancelar_un_registro_de_otro_dispositivo() {

        RegistroDemanda registro = registroActivo("dispositivo-dueno");

        when(demandaRepository.findById(1L))
                .thenReturn(Optional.of(registro));

        assertThatThrownBy(() ->
                demandaService.cancelarRegistro(1L, "otro-dispositivo"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no pertenece");

        assertThat(registro.getEstado())
                .isEqualTo(EstadoRegistroDemanda.ACTIVO);
    }

    @Test
    void no_permite_cancelar_dos_veces_el_mismo_registro() {

        RegistroDemanda registro = registroActivo("dispositivo-uno");
        registro.setEstado(EstadoRegistroDemanda.CANCELADO);

        when(demandaRepository.findById(1L))
                .thenReturn(Optional.of(registro));

        assertThatThrownBy(() ->
                demandaService.cancelarRegistro(1L, "dispositivo-uno"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya fue cancelado");
    }

    @Test
    void no_permite_cancelar_un_registro_expirado() {

        RegistroDemanda registro = registroActivo("dispositivo-uno");
        registro.setEstado(EstadoRegistroDemanda.EXPIRADO);

        when(demandaRepository.findById(1L))
                .thenReturn(Optional.of(registro));

        assertThatThrownBy(() ->
                demandaService.cancelarRegistro(1L, "dispositivo-uno"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("expir");
    }

    @Test
    void tampoco_permite_cancelar_si_el_tiempo_ya_expiro() {

        RegistroDemanda registro = registroActivo("dispositivo-uno");
        registro.setExpiraEn(Instant.now().minusSeconds(60));

        when(demandaRepository.findById(1L))
                .thenReturn(Optional.of(registro));

        assertThatThrownBy(() ->
                demandaService.cancelarRegistro(1L, "dispositivo-uno"))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("expir");
    }

    private RegistroDemanda registroActivo(String dispositivoId) {

        RegistroDemanda registro = new RegistroDemanda();

        registro.setId(1L);
        registro.setDispositivoId(dispositivoId);
        registro.setEstado(EstadoRegistroDemanda.ACTIVO);
        registro.setExpiraEn(Instant.now().plusSeconds(1200));

        return registro;
    }
}