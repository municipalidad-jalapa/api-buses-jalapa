package gt.muni.jalapa.ecoruta.flota.servicio;

/**
 * Resultado de dar de alta un equipo, con todo ya resuelto dentro de la
 * transaccion.
 *
 * <p>Existe para que la capa web no tenga que reconsultar la entidad ni navegar
 * su relacion con el vehiculo: {@code open-in-view} esta en false, asi que fuera
 * del servicio no hay sesion y tocar un proxy LAZY revienta.
 */
public record AltaDeEquipo(Long equipoId, CredencialEmitida credencial,
                           String etiqueta, String identificadorVehiculo) {

    /** El componente credencial lleva el secreto en claro. */
    @Override
    public String toString() {
        return "AltaDeEquipo[equipoId=%s, credencial=***, etiqueta=%s, vehiculo=%s]"
                .formatted(equipoId, etiqueta, identificadorVehiculo);
    }
}
