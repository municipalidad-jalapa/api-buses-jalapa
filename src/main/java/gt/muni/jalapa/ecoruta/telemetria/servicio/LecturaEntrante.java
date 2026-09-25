package gt.muni.jalapa.ecoruta.telemetria.servicio;

import java.time.Instant;

/**
 * Una lectura de GPS lista para registrar, venga del equipo a bordo o de una
 * integracion (SCRUM-24).
 *
 * @param velocidadKmh ya convertida a km/h; null si el origen no la informa
 * @param claveOrigen  identifica la lectura en su sistema de origen para no
 *                     registrarla dos veces; null si el origen no tiene una
 */
public record LecturaEntrante(double latitud, double longitud, Double velocidadKmh,
                              Instant registradoEn, String claveOrigen) {
}
