package gt.muni.jalapa.ecoruta.demanda.web;

import gt.muni.jalapa.ecoruta.demanda.servicio.HistoricoDemandaService;
import gt.muni.jalapa.ecoruta.demanda.web.dto.HistoricoDemandaResponse;
import gt.muni.jalapa.ecoruta.telemetria.servicio.HistoricoRecorridoService;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.HistoricoRecorridoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HU-85.
 *
 * Consultas historicas disponibles para el
 * administrador municipal.
 */
@RestController
@RequestMapping("/api/v1/admin/historico")
@RequiredArgsConstructor
public class HistoricoOperacionController {

    private final HistoricoDemandaService historicoDemandaService;
    private final HistoricoRecorridoService historicoRecorridoService;

    @GetMapping("/demanda")
    public ResponseEntity<HistoricoDemandaResponse> consultarDemanda(
            @RequestParam Long paradaId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha,

            @RequestParam(defaultValue = "0")
            int horaInicio,

            @RequestParam(defaultValue = "24")
            int horaFin
    ) {

        return ResponseEntity.ok(
                historicoDemandaService.consultar(
                        paradaId,
                        fecha,
                        horaInicio,
                        horaFin
                )
        );
    }

    /**
     * Consulta el recorrido historico de un bus
     * durante una fecha.
     */
    @GetMapping("/recorrido")
    public ResponseEntity<HistoricoRecorridoResponse> consultarRecorrido(
            @RequestParam Long vehiculoId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha
    ) {

        return ResponseEntity.ok(
                historicoRecorridoService.consultar(
                        vehiculoId,
                        fecha
                )
        );
    }
}