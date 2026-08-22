package gt.muni.jalapa.ecoruta.flota;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.dominio.Equipo;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-143 (HU-48), primer criterio: "entidad de dispositivo y su relacion con
 * vehiculo, con migracion Flyway".
 *
 * <p>Que estas pruebas corran ya implica que Hibernate valido el mapeo contra el
 * esquema de V5, porque ddl-auto esta en validate.
 */
class EsquemaFlotaTest extends IntegracionPostgisTest {

    @Autowired
    private VehiculoRepository vehiculos;

    @Autowired
    private EquipoRepository equipos;

    @Test
    void v5_sembro_el_vehiculo_piloto() {
        assertThat(vehiculos.findByIdentificador("BUS-01"))
                .get()
                .satisfies(v -> {
                    assertThat(v.getPlaca()).isEqualTo("P-000BBB");
                    assertThat(v.isActivo()).isTrue();
                    assertThat(v.getCreadoEn()).isNotNull();
                });
    }

    @Test
    void un_equipo_se_guarda_con_su_vehiculo_y_nace_activo() {
        Vehiculo bus = vehiculos.findByIdentificador("BUS-01").orElseThrow();

        Equipo guardado = equipos.save(
                new Equipo("eq_AAAAAAAAAAAA", "$2a$10$hashDePrueba", "Tableta cabina", bus));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getEstado()).isEqualTo(EstadoEquipo.ACTIVO);
        assertThat(guardado.getRevocadoEn()).isNull();
        assertThat(guardado.getCreadoEn()).isNotNull();
        assertThat(equipos.findByCodigoPublico("eq_AAAAAAAAAAAA")).isPresent();
    }

    @Test
    void la_base_impide_dos_equipos_activos_en_el_mismo_vehiculo() {
        Vehiculo bus = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        equipos.saveAndFlush(new Equipo("eq_BBBBBBBBBBBB", "$2a$10$hash", "Primera", bus));

        // uq_equipo_activo_por_vehiculo: la regla vive en la base, no solo en el
        // servicio, igual que uq_registro_activo_por_dispositivo de V1.
        assertThatThrownBy(() ->
                equipos.saveAndFlush(new Equipo("eq_CCCCCCCCCCCC", "$2a$10$hash", "Segunda", bus)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revocar_libera_el_vehiculo_para_un_equipo_nuevo() {
        Vehiculo bus = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        Equipo viejo = equipos.saveAndFlush(
                new Equipo("eq_DDDDDDDDDDDD", "$2a$10$hash", "Vieja", bus));

        viejo.revocar(Instant.now());
        equipos.saveAndFlush(viejo);

        Equipo nuevo = equipos.saveAndFlush(
                new Equipo("eq_EEEEEEEEEEEE", "$2a$10$hash", "Nueva", bus));

        assertThat(nuevo.getId()).isNotNull();
        // Y el viejo sigue ahi: equipos ES el historial de asignaciones.
        List<Equipo> historial = equipos.findByVehiculoIdOrderByCreadoEnDesc(bus.getId());
        assertThat(historial).hasSize(2);
        assertThat(equipos.findByVehiculoIdAndEstado(bus.getId(), EstadoEquipo.ACTIVO))
                .get()
                .extracting(Equipo::getCodigoPublico)
                .isEqualTo("eq_EEEEEEEEEEEE");
    }

    @Test
    void revocar_dos_veces_no_mueve_la_fecha_de_la_primera_revocacion() {
        Vehiculo bus = vehiculos.findByIdentificador("BUS-01").orElseThrow();
        Equipo equipo = equipos.saveAndFlush(
                new Equipo("eq_FFFFFFFFFFFF", "$2a$10$hash", "Unica", bus));

        Instant primera = Instant.parse("2026-08-17T10:00:00Z");
        equipo.revocar(primera);
        equipo.revocar(Instant.parse("2026-08-17T18:00:00Z"));

        assertThat(equipo.getRevocadoEn()).isEqualTo(primera);
    }

}
