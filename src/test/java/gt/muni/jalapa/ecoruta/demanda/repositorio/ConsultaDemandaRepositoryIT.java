package gt.muni.jalapa.ecoruta.demanda.repositorio;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-283: conteo agrupado de reservas vigentes por parada. */
class ConsultaDemandaRepositoryIT extends IntegracionPostgisTest {

    @Autowired
    private ConsultaDemandaRepository consulta;

    @Test
    void cuenta_solo_activa_y_renovada_en_una_sola_parada() {
        insertar("disp-activa", 1L, "ACTIVA");
        insertar("disp-renovada", 1L, "RENOVADA");
        insertar("disp-abordo", 1L, "ABORDO");
        insertar("disp-cancelada", 1L, "CANCELADA");
        insertar("disp-expirada", 1L, "EXPIRADA");
        insertar("disp-activo-legado", 1L, "ACTIVO");

        Map<Long, Long> conteos = consulta.contarReservasActivasPorParada(List.of(1L));

        assertThat(conteos).containsEntry(1L, 2L);
    }

    @Test
    void agrupa_varias_paradas_en_una_sola_consulta() {
        insertar("disp-p1-a", 1L, "ACTIVA");
        insertar("disp-p1-b", 1L, "RENOVADA");
        insertar("disp-p1-c", 1L, "CANCELADA");
        insertar("disp-p2-a", 2L, "ACTIVA");

        Map<Long, Long> conteos = consulta.contarReservasActivasPorParada(List.of(1L, 2L, 3L));

        assertThat(conteos).containsEntry(1L, 2L);
        assertThat(conteos).containsEntry(2L, 1L);
        assertThat(conteos).doesNotContainKey(3L);
    }

    @Test
    void una_coleccion_vacia_no_ejecuta_sql_invalido() {
        assertThat(consulta.contarReservasActivasPorParada(List.of())).isEmpty();
    }

    private void insertar(String dispositivoId, long paradaId, String estado) {
        jdbc.update("""
                INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en)
                VALUES (?, ?, ?, now(), now() + interval '30 minutes')
                """, dispositivoId, paradaId, estado);
    }
}
