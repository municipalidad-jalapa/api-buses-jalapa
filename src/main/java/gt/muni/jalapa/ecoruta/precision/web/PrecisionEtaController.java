package gt.muni.jalapa.ecoruta.precision.web;

import gt.muni.jalapa.ecoruta.precision.servicio.PrecisionEtaService;
import gt.muni.jalapa.ecoruta.precision.web.dto.PrecisionEtaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Precision del ETA", description = "Error agregado de las predicciones (HU-73)")
@RestController
@RequestMapping("/api/v1/rutas")
public class PrecisionEtaController {

    private final PrecisionEtaService precision;

    public PrecisionEtaController(PrecisionEtaService precision) {
        this.precision = precision;
    }

    @Operation(summary = "Error promedio y maximo del ETA, por ruta, parada y franja")
    @GetMapping("/{rutaId}/eta/precision")
    public PrecisionEtaResponse precision(@PathVariable Long rutaId,
                                          @RequestParam Instant desde,
                                          @RequestParam Instant hasta) {
        return precision.deLaRuta(rutaId, desde, hasta);
    }
}
