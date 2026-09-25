package gt.muni.jalapa.ecoruta.demanda.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-85.
 *
 * Respuesta preparada para mostrarse directamente
 * como una grafica de demanda.
 */
public record HistoricoDemandaResponse(
        Long paradaId,
        LocalDate fecha,
        int horaInicio,
        int horaFin,
        long totalReservas,
        List<PuntoDemandaHistoricaDto> serie
) {
}