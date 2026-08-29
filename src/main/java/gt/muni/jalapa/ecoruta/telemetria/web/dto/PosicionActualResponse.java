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
        @Schema(example = "BUS-01") String vehiculo) {

    public static PosicionActualResponse de(PosicionHistorica posicion, String vehiculo) {
        return new PosicionActualResponse(
                // Se lee con Geo por la misma razon por la que se escribe con Geo:
                // getY() es la latitud y getX() la longitud, no al reves (ADR-007).
                Geo.latitud(posicion.getUbicacion()),
                Geo.longitud(posicion.getUbicacion()),
                posicion.getVelocidadKmh(),
                posicion.getRegistradoEn(),
                vehiculo);
    }
}
