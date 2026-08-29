package gt.muni.jalapa.ecoruta.identidad.servicio;

import gt.muni.jalapa.ecoruta.identidad.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class EmisorDeJwtTest {

    private JwtProperties props;
    private EmisorDeJwt emisor;

    @BeforeEach
    void armar() {
        props = new JwtProperties("secreto-de-pruebas-con-mas-de-32-chars!!", 480);
        emisor = new EmisorDeJwt(props);
    }

    @Test
    void el_token_lleva_rol_conductor_y_vence_en_la_jornada() {
        SesionJwt sesion = emisor.emitirParaConductor("uid-1");

        assertThat(sesion.rol()).isEqualTo("conductor");
        assertThat(sesion.expiraEn()).isAfter(Instant.now().plus(7, ChronoUnit.HOURS));
        assertThat(emisor.leerConductor(sesion.token()))
                .get()
                .extracting(SesionJwt::rol, SesionJwt::subject)
                .containsExactly("conductor", "uid-1");
    }

    @Test
    void un_token_vencido_no_se_lee() {
        Instant haceUnaHora = Instant.now().minus(1, ChronoUnit.HOURS);
        String vencido = Jwts.builder()
                .subject("uid-1")
                .claim(EmisorDeJwt.CLAIM_ROL, EmisorDeJwt.ROL_CONDUCTOR)
                .issuedAt(Date.from(haceUnaHora.minusSeconds(60)))
                .expiration(Date.from(haceUnaHora))
                .signWith(props.clave())
                .compact();

        assertThat(emisor.leerConductor(vencido)).isEmpty();
    }

    @Test
    void un_token_sin_rol_de_conductor_no_se_lee() {
        String deAdmin = Jwts.builder()
                .subject("uid-admin")
                .claim(EmisorDeJwt.CLAIM_ROL, "admin")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(props.clave())
                .compact();

        assertThat(emisor.leerConductor(deAdmin)).isEmpty();
    }

    @Test
    void un_token_con_otra_firma_no_se_lee() {
        JwtProperties otraFirma = new JwtProperties("otro-secreto-de-pruebas-32-chars-min", 480);
        String ajeno = new EmisorDeJwt(otraFirma).emitirParaConductor("uid-1").token();

        assertThat(emisor.leerConductor(ajeno)).isEmpty();
    }
}
