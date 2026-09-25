package gt.muni.jalapa.ecoruta.panel.servicio;

import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import gt.muni.jalapa.ecoruta.catalogo.repositorio.RutaRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.identidad.PanelAdminProperties;
import gt.muni.jalapa.ecoruta.panel.web.dto.ServicioResponse;
import gt.muni.jalapa.ecoruta.panel.web.dto.ServicioResponse.Bus;
import gt.muni.jalapa.ecoruta.panel.web.dto.ServicioResponse.EstadoDelServicio;
import gt.muni.jalapa.ecoruta.panel.web.dto.ServicioResponse.RutaEnServicio;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Compone el estado del servicio completo para el panel municipal. Recorre
 * TODAS las rutas activas: el administrador no esta atado a ninguna (criterio 6).
 */
@Service
@RequiredArgsConstructor
public class ServicioMunicipal {

    private final RutaRepository rutas;
    private final VehiculoRepository vehiculos;
    private final TelemetriaService telemetria;
    private final PanelAdminProperties panel;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public ServicioResponse estado() {
        Instant ahora = reloj.instant();
        return new ServicioResponse(ahora, rutas.buscarActivasConParadas().stream()
                .map(ruta -> deRuta(ruta, ahora))
                .toList());
    }

    private RutaEnServicio deRuta(Ruta ruta, Instant ahora) {
        return vehiculos.findFirstByRutaIdAndActivoTrue(ruta.getId())
                .map(vehiculo -> {
                    PosicionActualResponse posicion = telemetria.posicionVigente(vehiculo.getId()).orElse(null);
                    return new RutaEnServicio(ruta.getId(), ruta.getNombre(), ruta.getParadas().size(),
                            new Bus(vehiculo.getId(), vehiculo.getIdentificador(), vehiculo.getPlaca()),
                            esReciente(posicion, ahora) ? EstadoDelServicio.EN_RUTA
                                    : EstadoDelServicio.SIN_DATOS_RECIENTES,
                            posicion);
                })
                .orElseGet(() -> new RutaEnServicio(ruta.getId(), ruta.getNombre(), ruta.getParadas().size(),
                        null, EstadoDelServicio.SIN_BUS, null));
    }

    private boolean esReciente(PosicionActualResponse posicion, Instant ahora) {
        return posicion != null
                && Duration.between(posicion.timestamp(), ahora).compareTo(panel.datosRecientes()) <= 0;
    }
}
