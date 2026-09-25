package gt.muni.jalapa.ecoruta.tiempo.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Tipo de dia operativo de una ruta (HU-72).
 *
 * <p>Jueves y domingo tienen horario propio. El resto —incluido el sabado—
 * usa el de dia habil.
 */
public enum TipoDeDia {
    HABIL,
    JUEVES,
    DOMINGO;

    public static TipoDeDia de(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia == DayOfWeek.THURSDAY) {
            return JUEVES;
        }
        if (dia == DayOfWeek.SUNDAY) {
            return DOMINGO;
        }
        return HABIL;
    }
}
