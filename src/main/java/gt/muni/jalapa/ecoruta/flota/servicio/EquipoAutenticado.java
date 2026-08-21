package gt.muni.jalapa.ecoruta.flota.servicio;

import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;

/**
 * El principal que queda en el SecurityContext cuando un equipo se autentica.
 *
 * <p>Deliberadamente NO lleva el secreto, ni el hash, ni la entidad JPA: solo lo
 * que el controlador necesita para atribuir las posiciones. Que la ingesta tome
 * el vehiculo de aqui y no del cuerpo de la peticion es lo que impide que un
 * equipo reporte posiciones a nombre de otro bus (SCRUM-143).
 */
public record EquipoAutenticado(Long equipoId, String codigoPublico,
                                Long vehiculoId, String identificadorVehiculo) {

    public static EquipoAutenticado de(Equipo equipo) {
        return new EquipoAutenticado(
                equipo.getId(),
                equipo.getCodigoPublico(),
                equipo.getVehiculo() != null ? equipo.getVehiculo().getId() : null,
                equipo.getVehiculo() != null ? equipo.getVehiculo().getIdentificador() : null);
    }

    public boolean tieneVehiculoAsignado() {
        return vehiculoId != null;
    }
}
