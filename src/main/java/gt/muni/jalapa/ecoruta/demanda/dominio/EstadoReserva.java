package gt.muni.jalapa.ecoruta.demanda.dominio;

/** Estados de {@code registros_espera}. */
public enum EstadoReserva {
    ACTIVA,
    RENOVADA,
    ABORDO,
    CANCELADA,
    EXPIRADA;

    public boolean vigenteParaAviso() {
        return this == ACTIVA || this == RENOVADA;
    }

    public boolean permiteAbordajePasajero() {
        return vigenteParaAviso();
    }

    /** El conductor puede corregir incluso despues de la respuesta del pasajero. */
    public boolean permiteAbordajeConductor() {
        return this == ACTIVA || this == RENOVADA || this == ABORDO || this == CANCELADA;
    }
}
