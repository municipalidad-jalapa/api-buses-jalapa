package gt.muni.jalapa.ecoruta.exportacion.web;

import gt.muni.jalapa.ecoruta.common.ApiError;
import gt.muni.jalapa.ecoruta.exportacion.servicio.ExportacionService;
import gt.muni.jalapa.ecoruta.exportacion.servicio.ExportacionService.Archivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Descarga de datos del servicio desde el panel municipal (HU Desarrollo-86).
 *
 * <p>Cuelga de {@code /api/v1/admin/**}, que {@code SecurityConfig} ya restringe a
 * {@code ROLE_ADMIN}: no hace falta una regla nueva y un endpoint nuevo bajo ese
 * prefijo nace cerrado.
 */
@Tag(name = "Panel municipal", description = "Supervision del servicio completo")
@RestController
@RequestMapping("/api/v1/admin/exportaciones")
@RequiredArgsConstructor
public class ExportacionAdminController {

    /** Tipo MIME oficial de .xlsx. */
    public static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacionService exportacion;

    @Operation(summary = "Descarga la demanda y los recorridos del servicio en una hoja de calculo",
            description = """
                    Solo administrador. Devuelve un archivo .xlsx con tres hojas: `Resumen`,
                    `Demanda` (por dia, ruta y parada) y `Recorridos` (por dia y bus).

                    El rango es por dias de Guatemala y es inclusivo en ambos extremos:
                    `desde=2026-09-01&hasta=2026-09-15` cubre del 1 al 15 completos. El maximo
                    por exportacion es `ecoruta.exportacion.rango-maximo-dias` (366).

                    **No expone datos que identifiquen a un pasajero**: solo totales. No lleva
                    identificadores de dispositivo ni de reserva ni la hora de una reserva
                    individual.

                    El nombre sugerido del archivo viaja en `Content-Disposition`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El archivo .xlsx",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Falta una fecha o no tiene formato AAAA-MM-DD",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesion",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sesion sin rol de administrador",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Rango invertido o mayor al maximo permitido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/servicio")
    public ResponseEntity<byte[]> servicio(
            @Parameter(description = "Primer dia, inclusive (AAAA-MM-DD)", example = "2026-09-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Ultimo dia, inclusive (AAAA-MM-DD)", example = "2026-09-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Archivo archivo = exportacion.exportar(desde, hasta);

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                // Es un reporte generado al vuelo: ni el navegador ni un proxy deben guardarlo.
                .cacheControl(CacheControl.noStore())
                .contentLength(archivo.contenido().length)
                .body(archivo.contenido());
    }
}
