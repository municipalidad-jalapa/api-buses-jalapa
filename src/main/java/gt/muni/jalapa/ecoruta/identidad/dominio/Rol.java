package gt.muni.jalapa.ecoruta.identidad.dominio;

/** Roles persistidos en {@code usuarios.rol}. El panel del conductor exige CONDUCTOR. */
public enum Rol {
    CONDUCTOR,
    ADMIN;

    public boolean esConductor() {
        return this == CONDUCTOR;
    }
}
