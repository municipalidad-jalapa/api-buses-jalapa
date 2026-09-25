package gt.muni.jalapa.ecoruta.flota.servicio;

import gt.muni.jalapa.ecoruta.IntegracionPostgisTest;
import gt.muni.jalapa.ecoruta.flota.dominio.EstadoEquipo;
import gt.muni.jalapa.ecoruta.flota.repositorio.EquipoRepository;
import gt.muni.jalapa.ecoruta.flota.repositorio.VehiculoRepository;
import gt.muni.jalapa.ecoruta.flota.seguridad.TokenDeEquipo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-142: emision, verificacion y revocacion de credenciales de equipo. */
class CredencialesTest extends IntegracionPostgisTest {

    @Autowired
    private EquipoService equipoService;

    @Autowired
    private GeneradorDeCredenciales generador;

    @Autowired
    private EquipoRepository equipos;

    @Autowired
    private VehiculoRepository vehiculos;

    private Long busPiloto() {
        return vehiculos.findByIdentificador("BUS-01").orElseThrow().getId();
    }

    @Test
    void la_credencial_generada_calza_con_el_formato_publicado() {
        CredencialEmitida credencial = generador.generar();

        assertThat(credencial.credencialCompleta())
                .matches(TokenDeEquipoFormato.PATRON.pattern())
                .hasSize(TokenDeEquipoFormato.LARGO_TOKEN);
        assertThat(credencial.codigoPublico()).startsWith("eq_");
        assertThat(credencial.secreto()).hasSize(TokenDeEquipoFormato.LARGO_SECRETO);
    }

    @Test
    void el_token_completo_cabe_en_el_limite_de_72_bytes_de_bcrypt() {
        // Un secreto mas largo lo truncaria bcrypt en silencio, y dos secretos
        // distintos podrian llegar a validar igual.
        assertThat(generador.generar().secreto().getBytes()).hasSizeLessThanOrEqualTo(72);
    }

    @Test
    void cada_credencial_es_distinta() {
        Set<String> vistos = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> vistos.add(generador.generar().credencialCompleta()));

        assertThat(vistos).hasSize(200);
    }

    @Test
    void el_secreto_en_claro_no_se_guarda_en_la_base() {
        CredencialEmitida credencial = equipoService.emitir(busPiloto(), "Tableta de prueba").credencial();

        String hashGuardado = equipos.findByCodigoPublico(credencial.codigoPublico())
                .orElseThrow().getSecretoHash();

        assertThat(hashGuardado)
                .doesNotContain(credencial.secreto())
                .startsWith("$2");
    }

    @Test
    void una_credencial_recien_emitida_autentica() {
        CredencialEmitida credencial = equipoService.emitir(busPiloto(), "Tableta").credencial();

        assertThat(equipoService.autenticar(tokenDe(credencial)))
                .get()
                .satisfies(equipo ->
                        assertThat(equipo.codigoPublico()).isEqualTo(credencial.codigoPublico()));
    }

    @Test
    void un_codigo_valido_con_el_secreto_de_otro_no_autentica() {
        CredencialEmitida buena = equipoService.emitir(busPiloto(), "Tableta").credencial();
        CredencialEmitida otra = generador.generar();

        assertThat(equipoService.autenticar(
                new TokenDeEquipo(buena.codigoPublico(), otra.secreto()))).isEmpty();
    }

    @Test
    void un_codigo_que_no_existe_no_autentica() {
        assertThat(equipoService.autenticar(tokenDe(generador.generar()))).isEmpty();
    }

    @Test
    void una_credencial_revocada_deja_de_autenticar_de_inmediato() {
        // Criterio (c) de SCRUM-142, sin reiniciar contexto ni esperar expiracion.
        AltaDeEquipo alta = equipoService.emitir(busPiloto(), "Tableta");
        TokenDeEquipo token = tokenDe(alta.credencial());

        assertThat(equipoService.autenticar(token)).isPresent();

        equipoService.revocar(alta.equipoId());

        assertThat(equipoService.autenticar(token)).isEmpty();
        assertThat(equipos.findById(alta.equipoId()).orElseThrow().getEstado())
                .isEqualTo(EstadoEquipo.REVOCADO);
    }

    @Test
    void revocar_un_equipo_no_afecta_a_los_demas() {
        // "poder revocar un solo equipo" del enunciado de la historia.
        AltaDeEquipo uno = equipoService.emitir(busPiloto(), "Tableta 1");
        Long otroBus = vehiculos.save(new gt.muni.jalapa.ecoruta.flota.dominio.Vehiculo(
                "BUS-97", "P-977XXX")).getId();
        AltaDeEquipo otro = equipoService.emitir(otroBus, "Tableta 2");

        equipoService.revocar(uno.equipoId());

        assertThat(equipoService.autenticar(tokenDe(uno.credencial()))).isEmpty();
        assertThat(equipoService.autenticar(tokenDe(otro.credencial()))).isPresent();
    }

    @Test
    void revocar_un_equipo_inexistente_da_recurso_no_encontrado() {
        assertThatThrownBy(() -> equipoService.revocar(999_999L))
                .hasMessageContaining("Equipo con id 999999 no existe");
    }

    @Test
    void la_credencial_emitida_no_filtra_el_secreto_en_su_toString() {
        CredencialEmitida credencial = generador.generar();

        assertThat(credencial.toString())
                .contains(credencial.codigoPublico())
                .doesNotContain(credencial.secreto())
                .doesNotContain(credencial.secretoHash());
    }

    @Test
    void equipos_no_referencia_a_la_tabla_usuarios() {
        // Criterio (a): la credencial del equipo es independiente de las cuentas
        // de identidad/. Se comprueba en el esquema, no de palabra.
        List<String> referenciadas = jdbc.queryForList("""
                SELECT ccu.table_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.constraint_column_usage ccu
                    ON tc.constraint_name = ccu.constraint_name
                 WHERE tc.table_name = 'equipos' AND tc.constraint_type = 'FOREIGN KEY'
                """, String.class);

        assertThat(referenciadas).doesNotContain("usuarios");
    }

    @Test
    void las_migraciones_no_sembraron_ninguna_credencial() {
        // Sembrarla obligaria a publicar una credencial valida en git.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos", Long.class)).isZero();
    }

    private static TokenDeEquipo tokenDe(CredencialEmitida credencial) {
        return new TokenDeEquipo(credencial.codigoPublico(), credencial.secreto());
    }
}
