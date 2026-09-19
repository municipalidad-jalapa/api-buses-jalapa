package gt.muni.jalapa.ecoruta.flota.web.dto;

import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;

public record VehiculoResponse(Long id, String identificador, String placa, boolean activo, Long rutaId) {

    public static VehiculoResponse de(Vehiculo vehiculo) {
        return new VehiculoResponse(vehiculo.getId(), vehiculo.getIdentificador(),
                vehiculo.getPlaca(), vehiculo.isActivo(), vehiculo.getRutaId());
    }
}
