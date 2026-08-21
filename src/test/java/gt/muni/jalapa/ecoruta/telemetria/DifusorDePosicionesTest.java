package gt.muni.jalapa.ecoruta.telemetria;

import gt.muni.jalapa.ecoruta.telemetria.servicio.PosicionVigenteActualizada;
import gt.muni.jalapa.ecoruta.telemetria.servicio.TelemetriaProperties;
import gt.muni.jalapa.ecoruta.telemetria.web.DifusorDePosiciones;
import gt.muni.jalapa.ecoruta.telemetria.web.dto.PosicionActualResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El reparto en si, sin levantar Spring: aqui interesa que la lista de
 * suscriptores se mantenga sana pase lo que pase con las conexiones.
 */
class DifusorDePosicionesTest {

    private DifusorDePosiciones difusor;

    @BeforeEach
    void crearDifusor() {
        difusor = new DifusorDePosiciones(new TelemetriaProperties(12, 30, 25, 3));
    }

    @Test
    void un_suscriptor_queda_registrado_al_conectarse() {
        difusor.suscribir();

        assertThat(difusor.cuantosSuscriptores()).isEqualTo(1);
    }

    // No hay prueba de emisor.complete() a proposito: sin el handler que Spring MVC
    // engancha al arrancar la peticion asincrona, complete() solo deja constancia y
    // no dispara onCompletion. El camino de limpieza se cubre abajo con la conexion
    // rota, y el cierre normal esta verificado contra la aplicacion real.

    @Test
    void un_suscriptor_roto_no_impide_difundir_a_los_demas_y_sale_de_la_lista() {
        // Sin el try/catch por emisor, uno con la conexion caida propagaria la
        // excepcion al hilo de la ingesta y tumbaria el reparto a los demas.
        difusor.suscribir();
        difusor.suscribir().completeWithError(new IOException("se cayo la red"));

        difusor.difundir(new PosicionVigenteActualizada(unaPosicion()));

        assertThat(difusor.cuantosSuscriptores()).isEqualTo(1);
    }

    @Test
    void difundir_sin_suscriptores_no_revienta() {
        difusor.difundir(new PosicionVigenteActualizada(unaPosicion()));

        assertThat(difusor.cuantosSuscriptores()).isZero();
    }

    @Test
    void el_latido_no_revienta_sin_suscriptores() {
        difusor.latir();

        assertThat(difusor.cuantosSuscriptores()).isZero();
    }

    private static PosicionActualResponse unaPosicion() {
        return new PosicionActualResponse(14.6335, -89.9885, 18.0, Instant.now(), "BUS-01");
    }
}
