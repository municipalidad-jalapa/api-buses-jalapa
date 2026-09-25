package gt.muni.jalapa.ecoruta.opiniones;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Opiniones del servicio (SCRUM-26, bloque A).
 *
 * @param limiteEnvios   cuantas opiniones puede mandar un mismo navegador en la
 *                       ventana; al excederse, 429
 * @param ventanaMinutos ventana del limite
 * @param textoMaximo    largo maximo del texto libre; la pantalla muestra el
 *                       mismo maximo con su contador
 */
@ConfigurationProperties("ecoruta.opiniones")
public record OpinionesProperties(int limiteEnvios, int ventanaMinutos, int textoMaximo) {

    public OpinionesProperties {
        limiteEnvios = limiteEnvios <= 0 ? 5 : limiteEnvios;
        ventanaMinutos = ventanaMinutos <= 0 ? 10 : ventanaMinutos;
        textoMaximo = textoMaximo <= 0 ? 500 : textoMaximo;
    }

    public Duration ventana() {
        return Duration.ofMinutes(ventanaMinutos);
    }
}
