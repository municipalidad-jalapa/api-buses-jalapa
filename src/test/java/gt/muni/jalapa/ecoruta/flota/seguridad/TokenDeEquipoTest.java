package gt.muni.jalapa.ecoruta.flota.seguridad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** El parseo del token nunca lanza: decide si el bearer es nuestro, y nada mas. */
class TokenDeEquipoTest {

    private static final String CODIGO = "eq_AbCdEfGhIjKl";
    private static final String SECRETO = "0123456789012345678901234567890123456789012";  // 43
    private static final String VALIDO = "Bearer " + CODIGO + "." + SECRETO;

    @Test
    void parte_un_token_bien_formado_en_sus_dos_mitades() {
        assertThat(TokenDeEquipo.parsear(VALIDO))
                .get()
                .satisfies(t -> {
                    assertThat(t.codigoPublico()).isEqualTo(CODIGO);
                    assertThat(t.secreto()).isEqualTo(SECRETO);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer esto.no.es.un.jwt",                 // el caso 3.7 de Postman
            "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.firma",  // un JWT de SCRUM-134
            "Bearer eq_corto.abc",
            "Bearer eq_AbCdEfGhIjKl",                   // sin secreto
            "Bearer eq_AbCdEfGhIjKl.",                  // secreto vacio
            "Basic eq_AbCdEfGhIjKl.0123456789012345678901234567890123456789012",
            "eq_AbCdEfGhIjKl.0123456789012345678901234567890123456789012",  // sin Bearer
            "Bearer ",
            "",
    })
    void devuelve_vacio_sin_lanzar_ante_cualquier_cabecera_que_no_sea_nuestra(String cabecera) {
        assertThat(TokenDeEquipo.parsear(cabecera)).isEmpty();
    }

    @Test
    void una_cabecera_ausente_no_revienta() {
        assertThat(TokenDeEquipo.parsear(null)).isEmpty();
    }

    @Test
    void una_cabecera_enorme_se_descarta_por_largo_antes_de_mirar_el_contenido() {
        String gigante = "Bearer " + CODIGO + "." + "A".repeat(100_000);

        assertThat(TokenDeEquipo.parsear(gigante)).isEmpty();
    }

    @Test
    void el_toString_no_filtra_el_secreto() {
        TokenDeEquipo token = TokenDeEquipo.parsear(VALIDO).orElseThrow();

        assertThat(token.toString())
                .contains(CODIGO)
                .doesNotContain(SECRETO);
    }
}
