package gt.muni.jalapa.ecoruta.demanda.web.dto;

/**
 * HU-85.
 *
 * Representa un punto de la grafica de demanda.
 */
public record PuntoDemandaHistoricaDto(
        int hora,
        String etiqueta,
        long cantidad
) {
}
