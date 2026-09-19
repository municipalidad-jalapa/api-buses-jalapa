package gt.muni.jalapa.ecoruta.demanda.servicio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Tarea programada que expira las reservas vencidas sin intervencion manual
 * (Desarrollo-135). El planificador ya esta activo con {@code @EnableScheduling}
 * en {@code EcoRutaApplication}.
 *
 * <p>Toda la regla vive en {@link ReservaService#expirarVencidas()}; aqui solo
 * esta la cadencia. Se lee la propiedad con SpEL sobre el string y no sobre el
 * bean por lo mismo que {@code DifusorDePosiciones}: los records de
 * {@code @ConfigurationPropertiesScan} quedan registrados con un nombre
 * calificado y {@code #{@demandaProperties...}} no resolveria.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiradorDeReservas {

    private final ReservaService reservas;

    @Scheduled(fixedDelayString = "${ecoruta.demanda.barrido-segundos:60}",
            timeUnit = TimeUnit.SECONDS)
    public void barrer() {
        try {
            reservas.expirarVencidas();
        } catch (RuntimeException e) {
            // Que un fallo puntual no mate el planificador: la proxima pasada reintenta.
            log.warn("Fallo el barrido de reservas vencidas; se reintenta en la siguiente pasada", e);
        }
    }
}
