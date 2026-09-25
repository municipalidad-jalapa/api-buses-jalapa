package gt.muni.jalapa.ecoruta.flota.web.dto;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;

/** gps: uniqueId en Traccar del GPS que lleva el bus, o null si no tiene. */
public record VehiculoResponse(Long id, String identificador, String placa, boolean activo, Long rutaId,
                               Integer capacidad, String gps) {

    public static VehiculoResponse de(Vehiculo vehiculo, String gps) {
        return new VehiculoResponse(vehiculo.getId(), vehiculo.getIdentificador(),
                vehiculo.getPlaca(), vehiculo.isActivo(), vehiculo.getRutaId(), vehiculo.getCapacidad(), gps);
    }
}
