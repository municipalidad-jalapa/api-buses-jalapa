package gt.muni.jalapa.ecoruta.panel.web;

import gt.muni.jalapa.ecoruta.panel.servicio.ConteoDeAbordajes;
import gt.muni.jalapa.ecoruta.panel.servicio.ConteoDeAbordajes.Filtros;
import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.ConteoDeAbordajesResponse;
import gt.muni.jalapa.ecoruta.panel.web.dto.AbordajesDtos.Granularidad;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Pasajeros subidos, para el panel municipal (SCRUM-26, bloque F, criterio 1).
 *
 * <p>Cuelga de {@code /api/v1/admin/**}, que ya exige rol de municipalidad; el
 * SuperAdmin tambien entra porque su token lleva las dos autoridades.
 */
@Tag(name = "Panel — abordajes", description = "Cuantos pasajeros subieron, por ruta, vehiculo y periodo")
@RestController
@RequiredArgsConstructor
public class AbordajesAdminController {

    private final ConteoDeAbordajes conteo;

    @Operation(summary = "Pasajeros subidos por ruta, vehiculo y periodo",
            description = """
                    Cuenta los abordajes marcados por el piloto, que es el dato que
                    prevalece. Sin filtros de fecha devuelve todo el historial;
                    `granularidad` agrupa por dia (predeterminado), semana o mes.""")
    @GetMapping("/api/v1/admin/abordajes")
    public ConteoDeAbordajesResponse contar(
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) Long vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) String granularidad) {
        return conteo.contar(new Filtros(rutaId, vehiculoId, desde, hasta,
                Granularidad.de(granularidad)));
    }
}
