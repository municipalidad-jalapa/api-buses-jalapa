# language: es
@SCRUM-306 @backend @demanda
Característica: Reservar un lugar en una parada
  Como pasajero que espera en una parada
  Quiero indicar que estoy esperando el bus
  Para que el conductor sepa que debe detenerse por mí

  Escenario: Crear una reserva estando cerca de la parada
    Dado que existe una parada
    Y el pasajero se encuentra dentro de la geocerca de la parada
    Y el dispositivo no tiene una reserva vigente
    Cuando el pasajero indica que está esperando el bus
    Entonces la respuesta tiene estado HTTP 201
    Y la reserva queda registrada en estado "ACTIVA"
    Y la respuesta incluye el identificador, la parada y el momento de expiración

  Escenario: Rechazar una reserva cuando el pasajero está lejos
    Dado que existe una parada
    Y el pasajero se encuentra fuera de la geocerca de la parada
    Cuando el pasajero indica que está esperando el bus
    Entonces la respuesta tiene estado HTTP 422
    Y se informa que debe acercarse a la parada
    Y no se crea ninguna reserva

  Escenario: Impedir dos reservas vigentes para el mismo dispositivo
    Dado que el dispositivo ya tiene una reserva en estado "ACTIVA"
    Cuando el mismo dispositivo intenta reservar nuevamente
    Entonces la respuesta tiene estado HTTP 422
    Y solamente permanece una reserva vigente para el dispositivo

  Esquema del escenario: Rechazar una solicitud con campos obligatorios ausentes
    Dado que la solicitud no contiene el campo "<campo>"
    Cuando se intenta crear la reserva
    Entonces la respuesta tiene estado HTTP 400
    Y la respuesta utiliza el formato de error de la API

    Ejemplos:
      | campo         |
      | dispositivoId |
      | paradaId      |
      | latitud       |
      | longitud      |

  Escenario: Informar que la parada no existe
    Dado que se solicita una parada inexistente
    Cuando el pasajero intenta crear la reserva
    Entonces la respuesta tiene estado HTTP 404
    Y la respuesta utiliza el formato de error de la API
