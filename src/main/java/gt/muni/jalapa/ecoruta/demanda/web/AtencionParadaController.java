package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.demanda.servicio.AtencionParadaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.AtenderParadaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rutas")
@RequiredArgsConstructor
public class AtencionParadaController {

    private final AtencionParadaService servicio;

    @PostMapping("/{rutaId}/paradas/{paradaId}/atendida")
    public ResponseEntity<AtenderParadaResponse> atender(
            @PathVariable Long rutaId,
            @PathVariable Long paradaId,
            Authentication autenticacion
    ) {
        AtenderParadaResponse respuesta =
                servicio.atender(
                        rutaId,
                        paradaId,
                        autenticacion.getName()
                );

        return ResponseEntity.ok(respuesta);
    }
}