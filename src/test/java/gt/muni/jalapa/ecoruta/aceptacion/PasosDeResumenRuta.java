package gt.muni.jalapa.ecoruta.aceptacion;

import gt.muni.jalapa.ecoruta.catalogo.web.dto.ParadaResponse;
import gt.muni.jalapa.ecoruta.common.RecursoNoEncontradoException;
import gt.muni.jalapa.ecoruta.demanda.servicio.ResumenRutaCompuesto;
import gt.muni.jalapa.ecoruta.demanda.servicio.ResumenRutaService;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Traduce los pasos de {@code componer_resumen_de_ruta.feature} (SCRUM-282/283). */
public class PasosDeResumenRuta {

    private static final long RUTA_EJEMPLO = 1L;

    @Autowired
    private ResumenRutaService resumenRuta;

    @Autowired
    private JdbcTemplate jdbc;

    private ResumenRutaCompuesto ultimoResumen;
    private long paradaEnFoco = 1L;
    private long rutaConsultada = RUTA_EJEMPLO;

    @Dado("que existe la ruta de ejemplo con sus paradas")
    public void que_existe_la_ruta_de_ejemplo() {
        rutaConsultada = RUTA_EJEMPLO;
    }

    @Dado("que el bus todavía no tiene una posición reportada")
    public void que_el_bus_no_tiene_posicion() {
        // IntegracionPostgisTest ya trunca posiciones_historicas antes de cada escenario.
    }

    @Dado("que una parada tiene reservas ACTIVA, RENOVADA, ABORDO, CANCELADA y EXPIRADA")
    public void que_una_parada_tiene_todos_los_estados() {
        paradaEnFoco = 1L;
        insertar("bdd-activa", paradaEnFoco, "ACTIVA");
        insertar("bdd-renovada", paradaEnFoco, "RENOVADA");
        insertar("bdd-abordo", paradaEnFoco, "ABORDO");
        insertar("bdd-cancelada", paradaEnFoco, "CANCELADA");
        insertar("bdd-expirada", paradaEnFoco, "EXPIRADA");
    }

    @Dado("que una parada de la ruta no tiene reservas vigentes")
    public void que_una_parada_no_tiene_reservas() {
        paradaEnFoco = 2L;
    }

    @Cuando("se compone la información de la ruta")
    public void se_compone_la_informacion_de_la_ruta() {
        ultimoResumen = resumenRuta.componer(rutaConsultada);
    }

    @Cuando("se intenta componer la información de una ruta que no existe")
    public void se_intenta_componer_ruta_inexistente() {
        rutaConsultada = 999_999L;
    }

    @Entonces("las paradas están ordenadas por su secuencia")
    public void las_paradas_estan_ordenadas() {
        List<Integer> ordenes = ultimoResumen.ruta().paradas().stream()
                .map(ParadaResponse::orden)
                .toList();

        assertThat(ordenes).isSorted();
        assertThat(ordenes.getFirst()).isEqualTo(1);
        assertThat(ordenes.getLast()).isEqualTo(8);
        assertThat(ultimoResumen.ruta().paradas()).hasSize(8);
    }

    @Entonces("la composición se obtiene correctamente")
    public void la_composicion_se_obtiene_correctamente() {
        assertThat(ultimoResumen).isNotNull();
        assertThat(ultimoResumen.ruta().id()).isEqualTo(RUTA_EJEMPLO);
    }

    @Y("la posición actual está vacía")
    public void la_posicion_actual_esta_vacia() {
        assertThat(ultimoResumen.posicionActual()).isNull();
    }

    @Entonces("esa parada tiene {int} reservas activas")
    public void esa_parada_tiene_reservas_activas(int cantidad) {
        assertThat(ultimoResumen.reservasActivasPorParada()).containsEntry(paradaEnFoco, (long) cantidad);
    }

    @Entonces("se produce un error de recurso no encontrado")
    public void se_produce_error_de_recurso_no_encontrado() {
        assertThatThrownBy(() -> resumenRuta.componer(rutaConsultada))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private void insertar(String dispositivoId, long paradaId, String estado) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, now(), now() + interval '30 minutes')
                """, dispositivoId, paradaId, estado);
    }
}
