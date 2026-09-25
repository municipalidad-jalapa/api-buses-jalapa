package gt.muni.jalapa.ecoruta.demanda.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcupacionDtoTest {

    @Test
    void el_nivel_sale_de_la_proporcion_de_la_capacidad() {
        assertThat(OcupacionDto.nivelDe(17, 30)).isEqualTo("HAY_LUGAR");
        assertThat(OcupacionDto.nivelDe(18, 30)).isEqualTo("CASI_LLENO");
        assertThat(OcupacionDto.nivelDe(26, 30)).isEqualTo("CASI_LLENO");
        assertThat(OcupacionDto.nivelDe(27, 30)).isEqualTo("LLENO");
        assertThat(OcupacionDto.nivelDe(40, 30)).isEqualTo("LLENO");
    }

    @Test
    void sin_capacidad_no_hay_nivel() {
        assertThat(OcupacionDto.nivelDe(12, null)).isNull();
    }
}
