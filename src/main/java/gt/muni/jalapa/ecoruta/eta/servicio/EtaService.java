package gt.muni.jalapa.ecoruta.eta.servicio;

import gt.muni.jalapa.ecoruta.eta.EtaProperties;
import gt.muni.jalapa.ecoruta.eta.servicio.CalculadorDeEta.EtaCalculado;
import gt.muni.jalapa.ecoruta.eta.web.dto.EtaRutaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda el ultimo ETA de cada ruta y decide cuando recalcularlo (SCRUM-166).
 *
 * <p>Cache en memoria: el piloto corre en una sola instancia y el ETA se
 * reconstruye solo con la siguiente posicion, asi que perderlo al reiniciar no
 * cuesta nada.
 */
@Service
@RequiredArgsConstructor
public class EtaService {

    private final CalculadorDeEta calculador;
    private final EtaProperties propiedades;
    private final Clock reloj;

    private final Map<Long, EtaCalculado> ultimos = new ConcurrentHashMap<>();

    /** Cuando se recalculo por ultima vez cada ruta a raiz de una posicion nueva. */
    private final Map<Long, Instant> recalculadoEn = new ConcurrentHashMap<>();

    /**
     * El ETA vigente de la ruta. Si la posicion con la que se calculo ya
     * envejecio, se entrega sin estimacion aunque no hayan llegado posiciones
     * nuevas: un numero viejo no se sirve como bueno.
     */
    public EtaRutaResponse consultar(Long rutaId) {
        EtaCalculado eta = ultimos.get(rutaId);
        if (eta == null) {
            eta = calculador.calcular(rutaId);
            ultimos.put(rutaId, eta);
        }
        if (eta.posicionEn() != null && calculador.esVieja(eta.posicionEn(), reloj.instant())) {
            return eta.respuesta().sinEstimacion();
        }
        return eta.respuesta();
    }

    /**
     * Recalcula a raiz de una posicion nueva, salvo que la ultima vez haya sido
     * hace menos del intervalo minimo.
     *
     * @return true si recalculo
     */
    public boolean recalcularSiCorresponde(Long rutaId, Instant ahora) {
        Instant anterior = recalculadoEn.get(rutaId);
        if (anterior != null
                && Duration.between(anterior, ahora).compareTo(propiedades.intervaloMinimoRecalculo()) < 0) {
            return false;
        }
        recalculadoEn.put(rutaId, ahora);
        ultimos.put(rutaId, calculador.calcular(rutaId));
        return true;
    }

    /** Solo para pruebas: el contexto de Spring se comparte entre clases. */
    public void olvidarTodo() {
        ultimos.clear();
        recalculadoEn.clear();
    }
}
