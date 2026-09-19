package gt.muni.jalapa.ecoruta.exportacion.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.exportacion.ExportacionProperties;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaDemanda;
import gt.muni.jalapa.ecoruta.exportacion.repositorio.ExportacionRepository.FilaRecorrido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Exporta la demanda y los recorridos del servicio a una hoja de calculo para los
 * informes de la Municipalidad (HU Desarrollo-86).
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    private final ExportacionRepository repositorio;
    private final ExportacionProperties propiedades;
    private final Clock reloj;

    /** El archivo listo para descargar. */
    public record Archivo(String nombre, byte[] contenido) {
    }

    /**
     * @param desde primer dia del rango, inclusive
     * @param hasta ultimo dia del rango, inclusive. Son dias de la zona horaria
     *              configurada: {@code hasta = 2026-09-15} incluye las 23:59 de ese dia
     * @throws ReglaDeNegocioException si el rango esta invertido o pasa del maximo (422)
     */
    @Transactional(readOnly = true)
    public Archivo exportar(LocalDate desde, LocalDate hasta) {
        validar(desde, hasta);

        ZoneId zona = propiedades.zona();
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        // El rango es inclusive por dias: el limite exclusivo es el inicio del dia siguiente.
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        List<FilaDemanda> demanda = repositorio.demandaPorDiaYParada(inicio, fin, zona);
        List<FilaRecorrido> recorridos = repositorio.recorridosPorDiaYBus(
                inicio, fin, zona, propiedades.saltoMaximoSegundos());

        byte[] contenido = LibroDelServicio.armar(desde, hasta, zona, reloj.instant(), demanda, recorridos);
        return new Archivo(nombreDelArchivo(desde, hasta), contenido);
    }

    static String nombreDelArchivo(LocalDate desde, LocalDate hasta) {
        return "ecoruta-servicio_" + desde + "_a_" + hasta + ".xlsx";
    }

    private void validar(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ReglaDeNegocioException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > propiedades.rangoMaximoDias()) {
            throw new ReglaDeNegocioException("El rango pedido es de " + dias + " dias y el maximo por exportacion es "
                    + propiedades.rangoMaximoDias() + ". Divide la consulta en varios periodos");
        }
    }
}
