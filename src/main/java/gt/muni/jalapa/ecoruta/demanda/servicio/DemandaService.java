package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoRegistroDemanda;
import gt.muni.jalapa.ecoruta.demanda.dominio.RegistroDemanda;
import gt.muni.jalapa.ecoruta.demanda.repositorio.DemandaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DemandaService {

    private final DemandaRepository demandaRepository;

    public DemandaService(DemandaRepository demandaRepository) {
        this.demandaRepository = demandaRepository;
    }

    @Transactional
    public void cancelarRegistro(Long registroId, String dispositivoId) {

        RegistroDemanda registro = demandaRepository.findById(registroId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Registro de demanda",
                                registroId
                        )
                );

        if (!registro.getDispositivoId().equals(dispositivoId)) {
            throw new AccessDeniedException(
                    "El registro no pertenece al dispositivo actual."
            );
        }

        if (registro.getEstado() == EstadoRegistroDemanda.CANCELADO) {
            throw new ReglaDeNegocioException(
                    "El registro ya fue cancelado."
            );
        }

        if (registro.getEstado() == EstadoRegistroDemanda.EXPIRADO
                || !registro.getExpiraEn().isAfter(Instant.now())) {

            throw new ReglaDeNegocioException(
                    "El registro ya expiró y no puede cancelarse."
            );
        }

        if (registro.getEstado() != EstadoRegistroDemanda.ACTIVO) {
            throw new ReglaDeNegocioException(
                    "El registro no se encuentra en un estado cancelable."
            );
        }

        registro.setEstado(EstadoRegistroDemanda.CANCELADO);
        registro.setCanceladoEn(Instant.now());

        demandaRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public long contarActivosPorParada(Long paradaId) {

        return demandaRepository.countByParada_IdAndEstado(
                paradaId,
                EstadoRegistroDemanda.ACTIVO
        );
    }
}