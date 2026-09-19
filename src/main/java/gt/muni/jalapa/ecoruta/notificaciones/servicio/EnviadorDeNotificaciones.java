package gt.muni.jalapa.ecoruta.notificaciones.servicio;

import gt.muni.jalapa.ecoruta.notificaciones.dominio.Aviso;

/**
 * Puerto de salida hacia FCM. Las pruebas sustituyen esta interfaz; la
 * implementacion real es {@link EnviadorFcm}.
 */
public interface EnviadorDeNotificaciones {

    void enviar(Aviso aviso);
}
