package gt.muni.jalapa.ecoruta.integraciones.traccar.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.integraciones.traccar.TraccarProperties;
import gt.muni.jalapa.ecoruta.integraciones.traccar.dominio.DispositivoExterno;
import gt.muni.jalapa.ecoruta.integraciones.traccar.repositorio.DispositivoExternoRepository;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar;
import gt.muni.jalapa.ecoruta.telemetria.servicio.LecturaEntrante;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.LoteAceptadoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recibe el reenvio de Traccar y lo registra como telemetria (SCRUM-24).
 *
 * <p>Todo o nada en la validacion: si un dato es invalido o un dispositivo no
 * esta asociado a un equipo activo con vehiculo, se responde 422 y no se
 * registra ninguna lectura del reenvio. Lo que si se descarta sin rechazar
 * (ventana de 12 h, reenvio repetido) lo decide la telemetria, igual que para
 * el equipo a bordo.
 *
 * <p>El registro llama directo a {@link TelemetriaService}: no hay un segundo
 * salto HTTP al propio backend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecepcionTraccar {

    private final DispositivoExternoRepository dispositivos;
    private final TelemetriaService telemetria;
    private final TraccarProperties traccar;

    @Transactional
    public LoteAceptadoResponse recibir(List<ReenvioTraccar> reenvios) {
        List<LecturaTraccar> lecturas = reenvios.stream()
                .map(reenvio -> LecturaTraccar.de(reenvio, traccar.unidadVelocidad()))
                .toList();

        // Se resuelven todos los dispositivos antes de registrar nada.
        Map<String, Equipo> equipos = new LinkedHashMap<>();
        for (LecturaTraccar lectura : lecturas) {
            equipos.computeIfAbsent(lectura.dispositivo(), this::equipoDe);
        }

        int recibidas = 0;
        int aceptadas = 0;
        int descartadas = 0;
        for (Map.Entry<String, Equipo> entrada : equipos.entrySet()) {
            List<LecturaEntrante> delEquipo = lecturas.stream()
                    .filter(lectura -> lectura.dispositivo().equals(entrada.getKey()))
                    .map(LecturaTraccar::lectura)
                    .toList();
            LoteAceptadoResponse parcial = telemetria.registrar(entrada.getValue(), delEquipo);
            recibidas += parcial.recibidas();
            aceptadas += parcial.aceptadas();
            descartadas += parcial.descartadas();
        }
        return new LoteAceptadoResponse(recibidas, aceptadas, descartadas);
    }

    private Equipo equipoDe(String identificador) {
        Equipo equipo = dispositivos.buscar(DispositivoExterno.TRACCAR, identificador)
                .map(DispositivoExterno::getEquipo)
                .orElseThrow(() -> {
                    log.warn("Reenvio de Traccar de un dispositivo no asociado: {}", identificador);
                    return new ReglaDeNegocioException(
                            "El dispositivo %s no esta asociado a ningun equipo.".formatted(identificador));
                });
        if (equipo.getEstado() != EstadoEquipo.ACTIVO || equipo.getVehiculo() == null) {
            throw new ReglaDeNegocioException(
                    "El dispositivo %s esta asociado a un equipo revocado o sin vehiculo.".formatted(identificador));
        }
        return equipo;
    }
}
