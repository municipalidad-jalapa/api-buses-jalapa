package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.demanda.repositorio.ConsultaDemandaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

/** Consulta de demanda vigente por parada (SCRUM-275). */
@Service
@RequiredArgsConstructor
public class DemandaService {

    private final ConsultaDemandaRepository consulta;

    @Transactional(readOnly = true)
    public Map<Long, Long> contarReservasActivasPorParada(Collection<Long> paradaIds) {
        return consulta.contarReservasActivasPorParada(paradaIds);
    }
}
