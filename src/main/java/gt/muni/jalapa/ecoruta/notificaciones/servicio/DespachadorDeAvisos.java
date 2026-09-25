package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.dominio.Aviso;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.FalloDeAviso;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.TipoAviso;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.DispositivoNotificacionRepository;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.FalloDeAvisoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Arma el aviso de una reserva y lo manda por el puerto de envio. Un fallo
 * (sin token, sin Firebase, FCM lo rechaza) se registra en
 * {@code fallos_de_aviso} y no se propaga: quien avisa no se detiene.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DespachadorDeAvisos {

    private final DispositivoNotificacionRepository dispositivos;
    private final FalloDeAvisoRepository fallos;
    private final EnviadorDeNotificaciones enviador;

    public void despachar(Long reservaId, String dispositivoId, TipoAviso tipo,
                          Long paradaId, String paradaNombre) {
        String token = dispositivos.findById(dispositivoId)
                .map(d -> d.getToken())
                .orElse("");
        Aviso aviso = new Aviso(reservaId, dispositivoId, token, tipo, paradaId, paradaNombre);
        try {
            enviador.enviar(aviso);
        } catch (RuntimeException ex) {
            FalloDeAviso fallo = new FalloDeAviso();
            fallo.setReservaId(reservaId);
            fallo.setTipo(tipo);
            fallo.setDetalle(ex.getMessage());
            fallo.setOcurridoEn(Instant.now());
            fallos.save(fallo);
            log.warn("Aviso no enviado: tipo={} reserva={} motivo={}", tipo, reservaId, ex.getMessage());
        }
    }
}
