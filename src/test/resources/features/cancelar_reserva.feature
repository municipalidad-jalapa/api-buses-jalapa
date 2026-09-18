# language: es
@SCRUM-172 @HU-77 @SCRUM-276 @HU-124 @backend
Característica: Cancelar mi registro

  Como pasajero que ya no va a viajar
  quiero quitar mi registro de la parada
  para no inflar el conteo de personas esperando

  @criterio-1
  Escenario: Cancelar con un toque una reserva activa
    Dado que existe una reserva activa de mi dispositivo
    Y el resumen muestra una persona esperando en la parada
    Cuando cancelo mi reserva
    Entonces la respuesta tiene codigo 204
    Y la reserva queda cancelada con fecha de cancelacion
    Y el resumen muestra cero personas esperando en la parada

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

  @criterio-7
  Escenario: No cancelar una reserva ya marcada como abordada
    Dado que mi reserva ya esta marcada como abordada
    Cuando intento cancelar mi reserva
    Entonces la respuesta tiene codigo 422
    Y la reserva continua marcada como abordada
    Y no se registra una fecha de cancelacion
