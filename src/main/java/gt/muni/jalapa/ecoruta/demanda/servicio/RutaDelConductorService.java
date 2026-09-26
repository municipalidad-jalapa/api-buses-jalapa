package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.common.AccesoDenegadoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.web.dto.RutaDelConductorResponse;
import gt.muni.jalapa.ecoruta.seguridad.repositorio.ConductorRutaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * El conductor elige la ruta que maneja al entrar al panel, o la cambia
 * durante la jornada. Queda en {@code usuarios.ruta_id}: el panel, la atencion
 * de paradas, el abordaje y los atrasos ya la leen de ahi, asi que el cambio
 * vale para todo en la peticion siguiente.
 *
 * <p>Solo rutas publicadas: un borrador no tiene al pasajero del otro lado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RutaDelConductorService {

    private final ConductorRutaRepository conductorRuta;
    private final RutaRepository rutas;

    @Transactional(readOnly = true)
    public RutaDelConductorResponse consultar(String conductor) {
        List<Ruta> publicadas = rutas.findAll().stream()
                .filter(Ruta::isActiva)
                .sorted(Comparator.comparing(Ruta::getId))
                .toList();
        Optional<Long> actual = conductorRuta.rutaDe(conductor);
        String nombre = actual.flatMap(rutas::findById).map(Ruta::getNombre).orElse(null);
        return new RutaDelConductorResponse(actual.orElse(null), nombre, publicadas.stream()
                .map(r -> new RutaDelConductorResponse.Opcion(r.getId(), r.getNombre()))
                .toList());
    }

    @Transactional
    public RutaDelConductorResponse elegir(String conductor, Long rutaId) {
        Ruta ruta = rutas.findById(rutaId)
                .filter(Ruta::isActiva)
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "Esa ruta no está publicada. Elegí una de la lista."));
        if (conductorRuta.asignar(conductor, ruta.getId()) == 0) {
            throw new AccesoDenegadoException("Esta cuenta no es un piloto activo.");
        }
        log.info("El conductor {} maneja ahora la ruta {} ({})", conductor, ruta.getId(), ruta.getNombre());
        return consultar(conductor);
    }
}
