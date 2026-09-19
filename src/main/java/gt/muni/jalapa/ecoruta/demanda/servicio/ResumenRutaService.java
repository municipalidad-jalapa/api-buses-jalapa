package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CatalogoService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.demanda.web.dto.ResumenRutaResponse;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Orquesta ruta, posicion y demanda para la pantalla del pasajero (SCRUM-283). */
@Service
@RequiredArgsConstructor
public class ResumenRutaService {

    private final CatalogoService catalogo;
    private final TelemetriaService telemetria;
    private final DemandaService demanda;

    /**
     * El resumen ya mapeado al DTO del contrato REST (SCRUM-284).
     *
     * <p>El mapeo de entidades a DTO se hace aqui, en la capa de servicio: el
     * controller solo publica lo que devuelve este metodo. {@code calculadoEn} se
     * fija al momento de la consulta y se serializa en ISO-8601 UTC.
     */
    @Transactional(readOnly = true)
    public ResumenRutaResponse resumir(Long rutaId) {
        Instant calculadoEn = Instant.now();
        return ResumenRutaResponse.de(componer(rutaId), calculadoEn);
    }

    @Transactional(readOnly = true)
    public ResumenRutaCompuesto componer(Long rutaId) {
        RutaResponse ruta = catalogo.buscar(rutaId);

        List<Long> paradaIds = ruta.paradas().stream().map(ParadaResponse::id).toList();
        Map<Long, Long> conteos = demanda.contarReservasActivasPorParada(paradaIds);

        Map<Long, Long> conteosCompletos = new LinkedHashMap<>();
        for (Long paradaId : paradaIds) {
            conteosCompletos.put(paradaId, conteos.getOrDefault(paradaId, 0L));
        }

        // Un bus por ruta (V12): la posicion es la del bus de esta ruta, nunca la
        // del ultimo bus que reporto en la flota.
        PosicionActualResponse posicion = telemetria.posicionVigentePorRuta(rutaId).orElse(null);

        return new ResumenRutaCompuesto(ruta, posicion, conteosCompletos);
    }
}
