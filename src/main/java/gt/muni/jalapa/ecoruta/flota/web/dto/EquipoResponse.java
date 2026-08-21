package gt.muni.jalapa.ecoruta.flota.web.dto;

import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Vista de un equipo para el listado.
 *
 * <p>No lleva el secreto ni el hash, y no puede llevarlos: se construye campo a
 * campo desde la entidad, nunca serializandola.
 */
@Schema(description = "Equipo a bordo. Nunca incluye el secreto ni su hash.")
public record EquipoResponse(
        Long id,
        String codigoPublico,
        String etiqueta,
        String vehiculo,
        String estado,
        Instant creadoEn,
        Instant revocadoEn,
        Instant ultimoUsoEn) {

    public static EquipoResponse de(Equipo equipo) {
        return new EquipoResponse(
                equipo.getId(),
                equipo.getCodigoPublico(),
                equipo.getEtiqueta(),
                equipo.getVehiculo() != null ? equipo.getVehiculo().getIdentificador() : null,
                equipo.getEstado().name(),
                equipo.getCreadoEn(),
                equipo.getRevocadoEn(),
                equipo.getUltimoUsoEn());
    }
}
