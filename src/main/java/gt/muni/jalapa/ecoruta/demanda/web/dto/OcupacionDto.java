package gt.muni.jalapa.ecoruta.demanda.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Cuanta gente lleva el bus de la ruta, segun lo que conto el piloto al cerrar
 * cada parada hoy (botones "Subió" y "Bajó" del panel en ruta).
 *
 * @param aBordo        subieron menos bajaron hoy, nunca negativo
 * @param capacidad     personas que caben; null si la Municipalidad no la cargo
 * @param nivel         HAY_LUGAR, CASI_LLENO o LLENO; null sin capacidad
 * @param actualizadaEn cuando el piloto cerro la ultima parada con conteo
 */
@Schema(description = "Ocupacion del bus segun el conteo del conductor")
public record OcupacionDto(
        @Schema(example = "12") int aBordo,
        @Schema(example = "30", nullable = true) Integer capacidad,
        @Schema(example = "HAY_LUGAR", nullable = true) String nivel,
        @Schema(example = "2026-09-25T15:04:00Z") Instant actualizadaEn) {

    /** Menos del 60 %: hay lugar. Del 60 al 90 %: casi lleno. Desde el 90 %: lleno. */
    public static String nivelDe(int aBordo, Integer capacidad) {
        if (capacidad == null || capacidad <= 0) {
            return null;
        }
        double razon = (double) aBordo / capacidad;
        if (razon < 0.6) {
            return "HAY_LUGAR";
        }
        return razon < 0.9 ? "CASI_LLENO" : "LLENO";
    }
}
