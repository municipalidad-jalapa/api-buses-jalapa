package gt.muni.jalapa.ecoruta.flota.dominio;

/**
 * Estado de la credencial de un equipo. No hay estado intermedio a proposito:
 * una credencial sirve o no sirve, y el cambio surte efecto en la siguiente
 * peticion (SCRUM-142, "una credencial revocada deja de ser aceptada de
 * inmediato").
 */
public enum EstadoEquipo {
    ACTIVO,
    REVOCADO
}
