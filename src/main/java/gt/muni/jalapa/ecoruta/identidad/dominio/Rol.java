package gt.muni.jalapa.ecoruta.identidad.dominio;

/**
 * Roles persistidos en {@code usuarios.rol}.
 *
 * <p>SCRUM-26, bloque D. El sistema tiene cuatro roles; tres viven aqui y el
 * cuarto, el pasajero, no es una cuenta de operacion: vive en {@code pasajeros}
 * y su rol viaja en su propio JWT (bloque B).
 *
 * <ul>
 *   <li>{@link #CONDUCTOR}: el piloto del bus, acotado a su ruta asignada.</li>
 *   <li>{@link #ADMIN}: la municipalidad; mira el panel, no administra cuentas.</li>
 *   <li>{@link #SUPERADMIN}: administra cuentas, rutas, paradas y vehiculos.</li>
 * </ul>
 */
public enum Rol {

    /** Piloto. El nombre se conserva por compatibilidad con lo ya guardado. */
    CONDUCTOR,

    /** Municipalidad. */
    ADMIN,

    /** Unico que crea, edita y desactiva cuentas de los demas roles. */
    SUPERADMIN;

    public boolean esConductor() {
        return this == CONDUCTOR;
    }

    /** Entra al panel municipal: la municipalidad y, por encima, el SuperAdmin. */
    public boolean entraAlPanelMunicipal() {
        return this == ADMIN || this == SUPERADMIN;
    }

    public boolean administraElSistema() {
        return this == SUPERADMIN;
    }
}
