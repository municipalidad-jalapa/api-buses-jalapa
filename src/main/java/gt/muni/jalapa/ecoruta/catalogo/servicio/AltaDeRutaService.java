package gt.muni.jalapa.ecoruta.catalogo.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.ParadaRepository;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.CorregirParadaRequest;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.demanda.dominio.EstadoReserva;
import gt.muni.jalapa.ecoruta.demanda.repositorio.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Rutas nuevas desde el panel municipal: se crean como borrador, se les agregan
 * paradas y trazado con el editor de "Corregir rutas", y se publican.
 *
 * <p>Un borrador no aparece al pasajero: una ruta sin paradas ni trazado solo
 * confundiria en el selector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AltaDeRutaService {

    private final RutaRepository rutas;
    private final ParadaRepository paradas;
    private final ReservaRepository reservas;

    @Transactional
    public RutaResponse crear(String nombre) {
        Ruta ruta = new Ruta();
        ruta.setNombre(nombre.strip());
        ruta.setActiva(false);
        return RutaResponse.de(rutas.save(ruta));
    }

    /** La parada va al final del recorrido; el orden se corrige arrastrando en el editor. */
    @Transactional
    public RutaResponse agregarParada(Long rutaId, CorregirParadaRequest peticion) {
        Ruta ruta = buscar(rutaId);
        int orden = ruta.getParadas().stream().mapToInt(Parada::getOrden).max().orElse(0) + 1;
        Parada parada = new Parada();
        parada.setNombre(peticion.nombre().strip());
        parada.setUbicacion(Geo.punto(peticion.latitud(), peticion.longitud()));
        parada.setOrden(orden);
        parada.setRuta(ruta);
        paradas.save(parada);
        ruta.getParadas().add(parada);
        return RutaResponse.de(ruta);
    }

    /**
     * Saca la parada del recorrido. No borra la fila: reservas, paradas
     * atendidas y predicciones la referencian y son el historial (V30).
     *
     * <p>Las reservas vigentes en ella se cancelan, porque el bus ya no pasa a
     * buscarlas, y las paradas siguientes suben un lugar.
     */
    @Transactional
    public RutaResponse eliminarParada(Long rutaId, Long paradaId, String quien) {
        Ruta ruta = buscar(rutaId);
        Parada parada = ruta.getParadas().stream()
                .filter(p -> p.getId().equals(paradaId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Parada", paradaId));
        if (ruta.isActiva() && ruta.getParadas().size() <= 2) {
            throw new ReglaDeNegocioException(
                    "Una ruta publicada necesita al menos 2 paradas. Ocultala primero para quitarle esta.");
        }

        Instant ahora = Instant.now();
        reservas.findByParada_IdAndEstadoIn(paradaId, EstadoReserva.RENOVABLES)
                .forEach(reserva -> reserva.cancelar(ahora));
        parada.setRetiradaEn(ahora);
        ruta.getParadas().remove(parada);
        paradas.flush();

        // De una en una y en orden: uq_parada_vigente_por_orden se revisa fila
        // por fila, y cada una baja al lugar que acaba de quedar libre.
        for (Parada siguiente : ruta.getParadas()) {
            if (siguiente.getOrden() > parada.getOrden()) {
                siguiente.setOrden(siguiente.getOrden() - 1);
                paradas.flush();
            }
        }
        log.info("Parada {} ({}) retirada de la ruta {} por {}", paradaId, parada.getNombre(), rutaId, quien);
        return RutaResponse.de(ruta);
    }

    @Transactional
    public RutaResponse publicar(Long rutaId, boolean activa) {
        Ruta ruta = buscar(rutaId);
        if (activa && (ruta.getParadas().size() < 2 || ruta.getTrazado() == null)) {
            throw new ReglaDeNegocioException(
                    "Para publicar la ruta necesita al menos 2 paradas y el trazado del recorrido.");
        }
        ruta.setActiva(activa);
        return RutaResponse.de(ruta);
    }

    private Ruta buscar(Long rutaId) {
        return rutas.findById(rutaId).orElseThrow(() -> new RecursoNoEncontradoException("Ruta", rutaId));
    }
}
