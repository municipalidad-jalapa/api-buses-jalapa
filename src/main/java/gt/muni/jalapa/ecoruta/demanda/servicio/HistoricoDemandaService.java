package gt.muni.jalapa.ecoruta.demanda.servicio;

import gt.muni.jalapa.ecoruta.demanda.repositorio.ConsultaDemandaRepository;
import gt.muni.jalapa.ecoruta.demanda.web.dto.HistoricoDemandaResponse;
import gt.muni.jalapa.ecoruta.demanda.web.dto.PuntoDemandaHistoricaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HU-85.
 *
 * Consulta la demanda historica por parada,
 * fecha y franja horaria.
 */
@Service
@RequiredArgsConstructor
public class HistoricoDemandaService {

    private static final ZoneId ZONA_GUATEMALA =
            ZoneId.of("America/Guatemala");

    private final ConsultaDemandaRepository consultaDemandaRepository;

    @Transactional(readOnly = true)
    public HistoricoDemandaResponse consultar(
            Long paradaId,
            LocalDate fecha,
            int horaInicio,
            int horaFin
    ) {

        validar(paradaId, fecha, horaInicio, horaFin);

        /*
         * Convertimos la fecha y las horas de Guatemala
         * a Instant para consultar correctamente TIMESTAMPTZ.
         */
        Instant desde = fecha
                .atStartOfDay(ZONA_GUATEMALA)
                .plusHours(horaInicio)
                .toInstant();

        Instant hasta = fecha
                .atStartOfDay(ZONA_GUATEMALA)
                .plusHours(horaFin)
                .toInstant();

        Map<Integer, Long> cantidades =
                consultaDemandaRepository.contarHistoricoPorHora(
                        paradaId,
                        desde,
                        hasta
                );

        /*
         * Incluimos tambien las horas que tengan cero reservas.
         *
         * Esto es importante para que la grafica no tenga
         * espacios faltantes.
         */
        List<PuntoDemandaHistoricaDto> serie =
                new ArrayList<>();

        long total = 0;

        for (int hora = horaInicio; hora < horaFin; hora++) {

            long cantidad =
                    cantidades.getOrDefault(hora, 0L);

            total += cantidad;

            serie.add(
                    new PuntoDemandaHistoricaDto(
                            hora,
                            String.format("%02d:00", hora),
                            cantidad
                    )
            );
        }

        return new HistoricoDemandaResponse(
                paradaId,
                fecha,
                horaInicio,
                horaFin,
                total,
                List.copyOf(serie)
        );
    }

    private void validar(
            Long paradaId,
            LocalDate fecha,
            int horaInicio,
            int horaFin
    ) {

        if (paradaId == null || paradaId <= 0) {
            throw new IllegalArgumentException(
                    "paradaId debe ser mayor que cero"
            );
        }

        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha es obligatoria"
            );
        }

        if (horaInicio < 0 || horaInicio > 23) {
            throw new IllegalArgumentException(
                    "horaInicio debe estar entre 0 y 23"
            );
        }

        /*
         * Permitimos 24 solamente como limite final.
         *
         * Ejemplo:
         * horaInicio=18
         * horaFin=24
         */
        if (horaFin < 1 || horaFin > 24) {
            throw new IllegalArgumentException(
                    "horaFin debe estar entre 1 y 24"
            );
        }

        if (horaInicio >= horaFin) {
            throw new IllegalArgumentException(
                    "horaInicio debe ser menor que horaFin"
            );
        }
    }
}