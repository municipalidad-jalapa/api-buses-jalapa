package gt.muni.jalapa.ecoruta.catalogo.servicio;

import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consulta de rutas y paradas (SCRUM-130). */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final RutaRepository rutas;

    /**
     * Las rutas activas con sus paradas.
     *
     * <p>Los DTO se arman dentro de la transaccion: open-in-view esta en false y
     * fuera de aqui las paradas LAZY ya no se pueden recorrer.
     */
    @Transactional(readOnly = true)
    public List<RutaResponse> listarActivas() {
        return rutas.buscarActivasConParadas().stream().map(RutaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public RutaResponse buscar(Long rutaId) {
        return rutas.buscarConParadas(rutaId)
                .map(RutaResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId));
    }
}
