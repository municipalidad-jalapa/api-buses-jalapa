package gt.muni.jalapa.ecoruta.precision.web.dto;

import java.util.List;

public record PrecisionEtaResponse(
        Long rutaId,
        double errorPromedioMin,
        double errorMaximoMin,
        List<ErrorPorParadaResponse> porParada,
        List<ErrorPorFranjaResponse> porFranja) {
}
