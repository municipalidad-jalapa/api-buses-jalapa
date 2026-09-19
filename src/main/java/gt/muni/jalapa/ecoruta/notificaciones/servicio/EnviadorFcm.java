package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import gt.muni.jalapa.ecoruta.identidad.config.ClienteFirebaseAuth;
import gt.muni.jalapa.ecoruta.notificaciones.dominio.Aviso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Envio real a Firebase Cloud Messaging. Usa el mismo {@code FirebaseApp}
 * que la verificacion del idToken del conductor: mismas credenciales
 * (Secure File / env), nunca un JSON en el repo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnviadorFcm implements EnviadorDeNotificaciones {

    private final ClienteFirebaseAuth firebase;

    @Override
    public void enviar(Aviso aviso) {
        if (!StringUtils.hasText(aviso.tokenNotificacion())) {
            throw new EnvioDeAvisoException(
                    "El dispositivo " + aviso.dispositivoId() + " no tiene token FCM");
        }
        FirebaseApp app = firebase.firebaseApp().orElseThrow(() ->
                new EnvioDeAvisoException("Firebase Admin no configurado: no se puede enviar FCM"));
        try {
            String id = FirebaseMessaging.getInstance(app).send(mensaje(aviso));
            log.info("Aviso FCM enviado: tipo={} reserva={} messageId={}",
                    aviso.tipo(), aviso.reservaId(), id);
        } catch (FirebaseMessagingException ex) {
            throw new EnvioDeAvisoException(
                    "FCM rechazo el aviso de reserva " + aviso.reservaId(), ex);
        }
    }

    private static Message mensaje(Aviso aviso) {
        return Message.builder()
                .setToken(aviso.tokenNotificacion())
                .setNotification(Notification.builder()
                        .setTitle(aviso.titulo())
                        .setBody(aviso.cuerpo())
                        .build())
                .putData("tipo", aviso.tipo().name())
                .putData("reservaId", String.valueOf(aviso.reservaId()))
                .putData("paradaId", String.valueOf(aviso.paradaId()))
                .putData("dispositivoId", aviso.dispositivoId())
                .build();
    }
}
