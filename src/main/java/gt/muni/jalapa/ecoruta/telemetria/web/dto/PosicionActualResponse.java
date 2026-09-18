package gt.muni.jalapa.ecoruta.telemetria.web.dto;

import gt.muni.jalapa.ecoruta.common.Geo;
import gt.muni.jalapa.ecoruta.telemetria.dominio.PosicionHistorica;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** La posicion vigente del bus. Publico: lo consume la pantalla del pasajero. */
@Schema(description = "Ultima posicion conocida")
public record PosicionActualResponse(
        @Schema(example = "14.6335") double latitud,
        @Schema(example = "-89.9885") double longitud,
        @Schema(example = "18") Double velocidadKmh,
        @Schema(example = "2026-08-17T10:00:00Z") Instant timestamp,
        @Schema(example = "BUS-01") String vehiculo,
        @Schema(description = "Ruta que recorre el bus; null si el bus no tiene ruta asignada",
                example = "1", nullable = true) Long rutaId) {

    /** Sin ruta: se conserva para quien no la necesita (y para las pruebas previas a V12). */
    public PosicionActualResponse(double latitud, double longitud, Double velocidadKmh,
                                  Instant timestamp, String vehiculo) {
        this(latitud, longitud, velocidadKmh, timestamp, vehiculo, null);
    }

    public static PosicionActualResponse de(PosicionHistorica posicion, String vehiculo) {
        return de(posicion, vehiculo, null);
    }

    public static PosicionActualResponse de(PosicionHistorica posicion, String vehiculo, Long rutaId) {
        return new PosicionActualResponse(
                // Se lee con Geo por la misma razon por la que se escribe con Geo:
                // getY() es la latitud y getX() la longitud, no al reves (ADR-007).
                Geo.latitud(posicion.getUbicacion()),
                Geo.longitud(posicion.getUbicacion()),
                posicion.getVelocidadKmh(),
                posicion.getRegistradoEn(),
                vehiculo,
                rutaId);
    }
}
