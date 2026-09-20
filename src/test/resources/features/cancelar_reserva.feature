 # language: es
@SCRUM-276 @HU-124 @backend
Característica: Cancelar reserva

  Como pasajero
  quiero cancelar una reserva que ya no necesito
  para liberar mi lugar y evitar una parada innecesaria del bus

  @criterio-1
  Escenario: Cancelar una reserva activa
    Dado que existe una reserva activa del dispositivo "dispositivo-uno"
    Entonces el conteo de reservas activas en la parada es 1
    Cuando el dispositivo "dispositivo-uno" cancela su reserva
    Entonces la respuesta tiene codigo 204
    Y la reserva queda en estado "CANCELADA"
    Y la reserva sigue almacenada y tiene fecha de cancelacion
    Y el conteo de reservas activas en la parada es 0

  @criterio-2
  Escenario: Cancelar una reserva inexistente
    Cuando el dispositivo "dispositivo-uno" intenta cancelar la reserva inexistente 999999
    Entonces la respuesta tiene codigo 404

  @criterio-3
  Escenario: Un dispositivo no puede cancelar la reserva de otro
    Dado que existe una reserva activa del dispositivo "dispositivo-dueno"
    Cuando otro dispositivo "dispositivo-ajeno" intenta cancelar la reserva
    Entonces la respuesta tiene codigo 403
    Y la reserva queda en estado "ACTIVA"

  @criterio-4
  Escenario: El mismo dispositivo puede reservar nuevamente despues de cancelar
    Dado que existe una reserva activa del dispositivo "dispositivo-reutilizable"
    Cuando el dispositivo "dispositivo-reutilizable" cancela su reserva
    Entonces la respuesta tiene codigo 204
    Cuando el mismo dispositivo crea otra reserva activa
    Entonces el conteo de reservas activas en la parada es 1

  @criterio-5
  Escenario: No se puede cancelar dos veces la misma reserva
    Dado que existe una reserva activa del dispositivo "dispositivo-uno"
    Cuando el dispositivo "dispositivo-uno" cancela su reserva
    Entonces la respuesta tiene codigo 204
    Cuando el dispositivo "dispositivo-uno" vuelve a cancelar la misma reserva
    Entonces la respuesta tiene codigo 422

  @criterio-6
  Escenario: No se puede cancelar una reserva expirada
    Dado que existe una reserva expirada del dispositivo "dispositivo-expirado"
    Cuando el dispositivo "dispositivo-expirado" cancela su reserva
    Entonces la respuesta tiene codigo 422