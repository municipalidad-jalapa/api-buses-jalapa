package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.dominio.DispositivoNotificacion;
import gt.muni.jalapa.ecoruta.notificaciones.repositorio.DispositivoNotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistroDeDispositivoService {

    private final DispositivoNotificacionRepository dispositivos;

    @Transactional
    public void registrar(String dispositivoId, String tokenNotificacion) {
        DispositivoNotificacion fila = dispositivos.findById(dispositivoId)
                .orElseGet(DispositivoNotificacion::new);
        fila.setDispositivoId(dispositivoId);
        fila.setToken(tokenNotificacion);
        fila.setActualizadoEn(Instant.now());
        dispositivos.save(fila);
    }
}
