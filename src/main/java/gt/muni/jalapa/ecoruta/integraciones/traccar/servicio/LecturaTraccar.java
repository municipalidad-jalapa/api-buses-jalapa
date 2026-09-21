package gt.muni.jalapa.ecoruta.integraciones.traccar.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.integraciones.traccar.TraccarProperties.UnidadVelocidad;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar;
import gt.muni.jalapa.ecoruta.telemetria.servicio.LecturaEntrante;

import java.time.Instant;

public record LecturaTraccar(String dispositivo, LecturaEntrante lectura) {

    public static LecturaTraccar de(ReenvioTraccar reenvio, UnidadVelocidad unidad) {
        if (reenvio == null || reenvio.position() == null) {
            throw new ReglaDeNegocioException("El reenvio no trae la posicion.");
        }
        ReenvioTraccar.Posicion posicion = reenvio.position();

        String dispositivo = reenvio.device() != null && reenvio.device().uniqueId() != null
                && !reenvio.device().uniqueId().isBlank()
                ? reenvio.device().uniqueId().trim()
                : null;
        if (dispositivo == null) {
            throw new ReglaDeNegocioException("El reenvio no trae el identificador del dispositivo (device.uniqueId).");
        }

        Double latitud = posicion.latitude();
        Double longitud = posicion.longitude();
        if (latitud == null || !Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new ReglaDeNegocioException("latitud ausente o fuera de rango");
        }
        if (longitud == null || !Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new ReglaDeNegocioException("longitud ausente o fuera de rango");
        }
        Double nudos = posicion.speed();
        if (nudos != null && (!Double.isFinite(nudos) || nudos < 0)) {
            throw new ReglaDeNegocioException("velocidad ausente o invalida");
        }
        Instant fecha = posicion.fixTime() != null ? posicion.fixTime() : posicion.deviceTime();
        if (fecha == null) {
            throw new ReglaDeNegocioException("La posicion no trae fecha (fixTime).");
        }

        String clave = posicion.id() != null && posicion.id() > 0
                ? "traccar:" + posicion.id()
                : "traccar:" + dispositivo + ":" + fecha.toEpochMilli();

        return new LecturaTraccar(dispositivo,
                new LecturaEntrante(latitud, longitud, unidad.aKmh(posicion.speed()), fecha, clave));
    }
}
