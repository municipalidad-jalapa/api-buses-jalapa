package gt.muni.jalapa.ecoruta.flota.web.dto;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;

public record VehiculoResponse(Long id, String identificador, String placa, boolean activo) {

    public static VehiculoResponse de(Vehiculo vehiculo) {
        return new VehiculoResponse(vehiculo.getId(), vehiculo.getIdentificador(),
                vehiculo.getPlaca(), vehiculo.isActivo());
    }
}
