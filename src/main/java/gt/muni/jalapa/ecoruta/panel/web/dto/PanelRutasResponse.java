package gt.muni.jalapa.ecoruta.panel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * El tablero municipal completo en una sola respuesta (HU-79).
 *
 * <p>Es un DTO de solo lectura. El mapeo vive en
 * {@link gt.muni.jalapa.ecoruta.panel.servicio.PanelService}: el controller
 * solo publica lo que ese servicio ya compuso.
 */
@Schema(description = "Rutas activas con su operacion en curso")
public record PanelRutasResponse(List<PanelRutaDto> rutas) {
}
