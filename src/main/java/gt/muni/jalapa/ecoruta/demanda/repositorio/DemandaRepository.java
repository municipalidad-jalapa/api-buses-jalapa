package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoRegistroDemanda;
import gt.muni.jalapa.ecoruta.demanda.dominio.RegistroDemanda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemandaRepository
        extends JpaRepository<RegistroDemanda, Long> {

    long countByParada_IdAndEstado(
            Long paradaId,
            EstadoRegistroDemanda estado
    );

    Optional<RegistroDemanda> findByIdAndDispositivoId(
            Long id,
            String dispositivoId
    );

    boolean existsByDispositivoIdAndEstado(
            String dispositivoId,
            EstadoRegistroDemanda estado
    );
}