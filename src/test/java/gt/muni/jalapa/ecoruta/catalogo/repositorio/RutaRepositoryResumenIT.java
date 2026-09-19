package gt.muni.jalapa.ecoruta.catalogo.repositorio;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Parada;
import gt.muni.jalapa.ecoruta.catalogo.dominio.Ruta;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-282: la ruta y sus paradas en una sola lectura con fetch join. */
class RutaRepositoryResumenIT extends IntegracionPostgisTest {

    @Autowired
    private RutaRepository rutas;

    @Test
    void buscarConParadas_encuentra_la_ruta_sembrada_con_ocho_paradas() {
        Ruta ruta = rutas.buscarConParadas(1L).orElseThrow();

        assertThat(ruta.getId()).isEqualTo(1L);
        assertThat(ruta.getParadas()).hasSize(8);
    }

    @Test
    void buscarConParadas_inicializa_las_paradas_sin_depender_de_open_session_in_view() {
        Ruta ruta = rutas.buscarConParadas(1L).orElseThrow();

        // Fuera de la transaccion del repositorio: si no hubo fetch join, aqui
        // reventaria al tocar la coleccion LAZY.
        assertThat(Hibernate.isInitialized(ruta.getParadas())).isTrue();
        assertThat(ruta.getParadas().getFirst().getNombre()).isNotBlank();
    }

    @Test
    void buscarConParadas_devuelve_las_paradas_ordenadas_por_secuencia() {
        Ruta ruta = rutas.buscarConParadas(1L).orElseThrow();

        List<Integer> ordenes = ruta.getParadas().stream().map(Parada::getOrden).toList();

        assertThat(ordenes).isSorted();
        assertThat(ordenes.getFirst()).isEqualTo(1);
        assertThat(ordenes.getLast()).isEqualTo(8);
    }
}
