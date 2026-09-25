package gt.muni.jalapa.ecoruta.precision.servicio;

import gt.muni.jalapa.ecoruta.precision.dominio.LlegadaReal;
import gt.muni.jalapa.ecoruta.precision.repositorio.LlegadaRealRepository;
import gt.muni.jalapa.ecoruta.precision.web.dto.ErrorPorFranjaResponse;
import gt.muni.jalapa.ecoruta.precision.web.dto.ErrorPorParadaResponse;
import gt.muni.jalapa.ecoruta.precision.web.dto.PrecisionEtaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Consulta agregada de error, siempre filtrada por una ruta (HU-73). */
@Service
public class PrecisionEtaService {

    private static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    private final LlegadaRealRepository llegadas;

    public PrecisionEtaService(LlegadaRealRepository llegadas) {
        this.llegadas = llegadas;
    }

    @Transactional(readOnly = true)
    public PrecisionEtaResponse deLaRuta(Long rutaId, Instant desde, Instant hasta) {
        List<LlegadaReal> muestras = llegadas.deLaRutaEntre(rutaId, desde, hasta);
        if (muestras.isEmpty()) {
            return new PrecisionEtaResponse(rutaId, 0.0, 0.0, List.of(), List.of());
        }

        double promedio = redondear(muestras.stream().mapToDouble(LlegadaReal::getErrorMin).average().orElse(0));
        double maximo = redondear(muestras.stream().mapToDouble(LlegadaReal::getErrorMin).max().orElse(0));

        List<ErrorPorParadaResponse> porParada = muestras.stream()
                .collect(Collectors.groupingBy(l -> l.getPrediccion().getParadaId()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entrada -> new ErrorPorParadaResponse(
                        entrada.getKey(),
                        redondear(entrada.getValue().stream().mapToDouble(LlegadaReal::getErrorMin).average().orElse(0)),
                        entrada.getValue().size()))
                .toList();

        List<ErrorPorFranjaResponse> porFranja = muestras.stream()
                .collect(Collectors.groupingBy(l -> franjaDe(l.getLlegadaEn())))
                .entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entrada -> new ErrorPorFranjaResponse(
                        entrada.getKey(),
                        redondear(entrada.getValue().stream().mapToDouble(LlegadaReal::getErrorMin).average().orElse(0))))
                .toList();

        return new PrecisionEtaResponse(rutaId, promedio, maximo, porParada, porFranja);
    }

    static String franjaDe(Instant llegadaEn) {
        ZonedDateTime local = llegadaEn.atZone(ZONA);
        int inicio = (local.getHour() / 3) * 3;
        return "%02d:00-%02d:00".formatted(inicio, inicio + 3);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
