package gt.muni.jalapa.ecoruta.panel.servicio;

import gt.muni.jalapa.ecoruta.catalogo.servicio.CatalogoService;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.catalogo.web.dto.RutaResponse;
import gt.muni.jalapa.ecoruta.demanda.servicio.DemandaService;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.panel.web.dto.PanelRutaDto;
import gt.muni.jalapa.ecoruta.panel.web.dto.PanelRutasResponse;
import gt.muni.jalapa.ecoruta.panel.web.dto.PosicionPanelDto;
import gt.muni.jalapa.ecoruta.panel.web.dto.ReservaPorParadaDto;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tablero municipal: todas las rutas activas en una sola lectura (HU-79).
 *
 * <p>Reutiliza el mismo trio que {@code ResumenRutaService} (catalogo,
 * telemetria, demanda), pero recorre TODAS las rutas activas. El panel no
 * puede encadenar un resumen por ruta: eso serian N idas al servidor y el
 * refresco se veria a trompicones.
 */
@Service
@RequiredArgsConstructor
public class PanelService {

    private final CatalogoService catalogo;
    private final VehiculoRepository vehiculos;
    private final TelemetriaService telemetria;
    private final DemandaService demanda;
    private final PanelProperties propiedades;

    /**
     * Arma el tablero. {@code vehiculoId} y {@code posicion} quedan null si la
     * ruta no tiene bus activo: no es un error, es una ruta sin unidad.
     */
    @Transactional(readOnly = true)
    public PanelRutasResponse listar() {
        List<RutaResponse> activas = catalogo.listarActivas();
        Instant umbral = Instant.now().minus(propiedades.umbralSinTransmitir());
        Map<Long, Long> conteos = demanda.contarReservasActivasPorParada(paradaIdsDe(activas));
        return new PanelRutasResponse(activas.stream()
                .map(ruta -> componer(ruta, umbral, conteos))
                .toList());
    }

    private PanelRutaDto componer(RutaResponse ruta, Instant umbral, Map<Long, Long> conteos) {
        // Una sola lectura del bus: posicionVigentePorRuta volveria a hacer esta
        // misma consulta. Con el Vehiculo ya en mano, la posicion se pide por id.
        Optional<Vehiculo> bus = vehiculos.findFirstByRutaIdAndActivoTrue(ruta.id());
        Long vehiculoId = bus.map(Vehiculo::getId).orElse(null);

        PosicionPanelDto posicion = bus
                .flatMap(vehiculo -> telemetria.posicionVigente(vehiculo.getId()))
                .map(PosicionPanelDto::de)
                .orElse(null);

        return new PanelRutaDto(
                ruta.id(),
                ruta.nombre(),
                vehiculoId,
                posicion,
                estaTransmitiendo(posicion, umbral),
                reservasDe(ruta, conteos));
    }

    private static boolean estaTransmitiendo(PosicionPanelDto posicion, Instant umbral) {
        return posicion != null && posicion.registradaEn().isAfter(umbral);
    }

    private static List<ReservaPorParadaDto> reservasDe(
            RutaResponse ruta, Map<Long, Long> conteos) {
        return ruta.paradas().stream()
                .map(parada -> new ReservaPorParadaDto(
                        parada.id(), conteos.getOrDefault(parada.id(), 0L)))
                .toList();
    }

    private static List<Long> paradaIdsDe(List<RutaResponse> rutas) {
        return rutas.stream()
                .flatMap(ruta -> ruta.paradas().stream())
                .map(ParadaResponse::id)
                .toList();
    }
}
