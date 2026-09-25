package gt.muni.jalapa.ecoruta.telemetria.servicio;

import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.telemetria.repositorio.PosicionHistoricaRepository;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.HistoricoRecorridoResponse;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PuntoRecorridoHistoricoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * HU-85.
 *
 * Consulta el recorrido historico de un vehiculo
 * durante una fecha determinada.
 */
@Service
@RequiredArgsConstructor
public class HistoricoRecorridoService {

    private static final ZoneId ZONA_GUATEMALA =
            ZoneId.of("America/Guatemala");

    private final PosicionHistoricaRepository repository;
    private final VehiculoRepository vehiculoRepository;

    @Transactional(readOnly = true)
    public HistoricoRecorridoResponse consultar(
            Long vehiculoId,
            LocalDate fecha
    ) {

        validar(vehiculoId, fecha);

        /*
         * HU-85 / observacion QA.
         *
         * Un vehiculo inexistente no debe confundirse
         * con un vehiculo real que no tuvo posiciones.
         */
        if (!vehiculoRepository.existsById(vehiculoId)) {
            throw new RecursoNoEncontradoException(
                    "Vehiculo",
                    vehiculoId
            );
        }

        Instant desde = fecha
                .atStartOfDay(ZONA_GUATEMALA)
                .toInstant();

        Instant hasta = fecha
                .plusDays(1)
                .atStartOfDay(ZONA_GUATEMALA)
                .toInstant();

        List<PuntoRecorridoHistoricoDto> puntos =
                repository
                        .findByVehiculoIdAndRegistradoEnGreaterThanEqualAndRegistradoEnLessThanOrderByRegistradoEnAscIdAsc(
                                vehiculoId,
                                desde,
                                hasta
                        )
                        .stream()
                        .map(posicion ->
                                new PuntoRecorridoHistoricoDto(
                                        posicion.getUbicacion().getY(),
                                        posicion.getUbicacion().getX(),
                                        posicion.getVelocidadKmh(),
                                        posicion.getRegistradoEn()
                                )
                        )
                        .toList();

        return new HistoricoRecorridoResponse(
                vehiculoId,
                fecha,
                puntos.size(),
                puntos
        );
    }

    private void validar(
            Long vehiculoId,
            LocalDate fecha
    ) {

        if (vehiculoId == null || vehiculoId <= 0) {
            throw new IllegalArgumentException(
                    "vehiculoId debe ser mayor que cero"
            );
        }

        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha es obligatoria"
            );
        }
    }
}