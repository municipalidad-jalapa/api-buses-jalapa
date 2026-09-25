package gt.muni.jalapa.ecoruta.integraciones.traccar.servicio;

import gt.muni.jalapa.ecoruta.common.ReglaDeNegocioException;
import gt.muni.jalapa.ecoruta.integraciones.traccar.TraccarProperties.UnidadVelocidad;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar.Dispositivo;
import gt.muni.jalapa.ecoruta.integraciones.traccar.web.dto.ReenvioTraccar.Posicion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LecturaTraccarTest {

    private static final Instant T = Instant.parse("2026-09-17T15:00:00Z");
    private static final Dispositivo DISPOSITIVO = new Dispositivo(3L, "860000000000001", "BUS-01");

    @Test
    void traduce_coordenadas_fecha_y_clave_de_origen() {
        LecturaTraccar lectura = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(42L, 3L, 14.63, -89.98, 10.0, T, null), DISPOSITIVO),
                UnidadVelocidad.NUDOS);

        assertThat(lectura.dispositivo()).isEqualTo("860000000000001");
        assertThat(lectura.lectura().latitud()).isEqualTo(14.63);
        assertThat(lectura.lectura().longitud()).isEqualTo(-89.98);
        assertThat(lectura.lectura().registradoEn()).isEqualTo(T);
        assertThat(lectura.lectura().claveOrigen()).isEqualTo("traccar:42");
    }

    @Test
    void convierte_la_velocidad_a_kilometros_por_hora_segun_la_unidad() {
        assertThat(kmh(10.0, UnidadVelocidad.NUDOS)).isCloseTo(18.52, within(1e-9));
        assertThat(kmh(10.0, UnidadVelocidad.MS)).isCloseTo(36.0, within(1e-9));
        assertThat(kmh(10.0, UnidadVelocidad.KMH)).isCloseTo(10.0, within(1e-9));
        assertThat(kmh(null, UnidadVelocidad.NUDOS)).isNull();
    }

    @Test
    void id_positivo_usa_la_clave_traccar_id() {
        LecturaTraccar lectura = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(42L, 3L, 14.63, -89.98, 0.0, T, null), DISPOSITIVO),
                UnidadVelocidad.NUDOS);

        assertThat(lectura.lectura().claveOrigen()).isEqualTo("traccar:42");
    }

    @Test
    void sin_id_de_posicion_la_clave_sale_del_dispositivo_y_la_fecha() {
        LecturaTraccar lectura = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(null, 3L, 14.63, -89.98, 0.0, null, T), DISPOSITIVO),
                UnidadVelocidad.NUDOS);

        assertThat(lectura.lectura().registradoEn()).isEqualTo(T);
        assertThat(lectura.lectura().claveOrigen()).isEqualTo("traccar:860000000000001:" + T.toEpochMilli());
    }

    @Test
    void position_id_cero_de_la_captura_real_no_se_usa_como_identidad() {
        LecturaTraccar lectura = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(0L, 3L, 14.63, -89.98, 12.5, T, null), DISPOSITIVO),
                UnidadVelocidad.NUDOS);

        assertThat(lectura.lectura().claveOrigen()).isEqualTo("traccar:860000000000001:" + T.toEpochMilli());
    }

    @Test
    void id_cero_o_negativo_se_trata_como_sin_id() {
        String esperada = "traccar:860000000000001:" + T.toEpochMilli();

        LecturaTraccar cero = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(0L, 3L, 14.63, -89.98, 0.0, T, null), DISPOSITIVO),
                UnidadVelocidad.NUDOS);
        LecturaTraccar negativo = LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(-5L, 3L, 14.63, -89.98, 0.0, T, null), DISPOSITIVO),
                UnidadVelocidad.NUDOS);

        assertThat(cero.lectura().claveOrigen()).isEqualTo(esperada);
        assertThat(negativo.lectura().claveOrigen()).isEqualTo(esperada);
    }

    @Test
    void rechaza_datos_invalidos_con_regla_de_negocio() {
        assertThatThrownBy(() -> de(new Posicion(1L, 3L, 91.0, -89.98, 0.0, T, null)))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> de(new Posicion(1L, 3L, 14.6, -181.0, 0.0, T, null)))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> de(new Posicion(1L, 3L, 14.6, -89.9, 0.0, null, null)))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> LecturaTraccar.de(new ReenvioTraccar(null, DISPOSITIVO), UnidadVelocidad.NUDOS))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(1L, 3L, 14.6, -89.9, 0.0, T, null), null), UnidadVelocidad.NUDOS))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> de(new Posicion(1L, 3L, 14.6, -89.9, -1.0, T, null)))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    private static Double kmh(Double velocidad, UnidadVelocidad unidad) {
        return LecturaTraccar.de(
                new ReenvioTraccar(new Posicion(1L, 3L, 14.63, -89.98, velocidad, T, null), DISPOSITIVO), unidad)
                .lectura().velocidadKmh();
    }

    private static LecturaTraccar de(Posicion posicion) {
        return LecturaTraccar.de(new ReenvioTraccar(posicion, DISPOSITIVO), UnidadVelocidad.NUDOS);
    }
}
