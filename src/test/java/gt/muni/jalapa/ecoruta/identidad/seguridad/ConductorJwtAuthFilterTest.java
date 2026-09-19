package gt.muni.jalapa.ecoruta.identidad.seguridad;

import gt.muni.jalapa.ecoruta.identidad.JwtProperties;
import gt.muni.jalapa.ecoruta.identidad.servicio.EmisorDeJwt;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConductorJwtAuthFilterTest {

    private EmisorDeJwt emisor;
    private ConductorJwtAuthFilter filtro;
    private FilterChain cadena;

    @BeforeEach
    void armar() {
        emisor = new EmisorDeJwt(new JwtProperties(
                "secreto-de-pruebas-con-mas-de-32-chars!!", 480));
        filtro = new ConductorJwtAuthFilter(emisor);
        cadena = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void un_jwt_de_conductor_deja_ROLE_CONDUCTOR_en_el_contexto() throws Exception {
        String token = emisor.emitirParaConductor("uid-1").token();
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly(ConductorJwtAuthFilter.ROL);
        verify(cadena).doFilter(eq(peticion), any());
    }

    @Test
    void un_bearer_eq_se_deja_para_el_filtro_del_equipo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer eq_AbCdEfGhIjKl.secreto");

        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void un_jwt_invalido_no_lanza_y_deja_el_contexto_vacio() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer esto.no.es.un.jwt");

        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
