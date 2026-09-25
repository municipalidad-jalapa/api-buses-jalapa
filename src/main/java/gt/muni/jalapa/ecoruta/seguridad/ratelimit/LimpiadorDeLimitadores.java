package gt.muni.jalapa.ecoruta.seguridad.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Barre cada diez minutos las claves inactivas de {@link RateLimitFilter}
 * (HU Desarrollo-95). Sin esto, cada IP y cada dispositivo que alguna vez
 * hicieron una peticion se quedan en memoria para siempre.
 */
@Component
@RequiredArgsConstructor
public class LimpiadorDeLimitadores {

    private final RateLimitFilter filtro;

    @Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void limpiar() {
        filtro.limpiar();
    }
}
