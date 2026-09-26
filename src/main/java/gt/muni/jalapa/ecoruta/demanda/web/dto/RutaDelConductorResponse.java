package gt.muni.jalapa.ecoruta.demanda.web.dto;

import java.util.List;

/**
 * La ruta que maneja el conductor y las que puede elegir.
 *
 * @param rutaId null si todavia no tiene ruta
 * @param rutas  las rutas publicadas, en orden de id
 */
public record RutaDelConductorResponse(Long rutaId, String rutaNombre, List<Opcion> rutas) {

    public record Opcion(Long id, String nombre) {
    }
}
