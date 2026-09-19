# language: es
@SCRUM-275 @SCRUM-282 @SCRUM-283 @backend
Característica: Componer los datos de arranque de una ruta

  Como pasajero
  quiero que los datos iniciales de la ruta se preparen juntos
  para que la pantalla pueda obtener después toda la información en una sola respuesta

  @SCRUM-282
  Escenario: La ruta se obtiene con sus paradas en el orden del recorrido
    Dado que existe la ruta de ejemplo con sus paradas
    Cuando se compone la información de la ruta
    Entonces las paradas están ordenadas por su secuencia

  @SCRUM-283
  Escenario: El resumen se puede componer aunque el bus aún no tenga posición
    Dado que existe la ruta de ejemplo con sus paradas
    Y que el bus todavía no tiene una posición reportada
    Cuando se compone la información de la ruta
    Entonces la composición se obtiene correctamente
    Y la posición actual está vacía

  @SCRUM-283
  Escenario: Solo las reservas vigentes se cuentan por parada
    Dado que existe la ruta de ejemplo con sus paradas
    Y que una parada tiene reservas ACTIVA, RENOVADA, ABORDO, CANCELADA y EXPIRADA
    Cuando se compone la información de la ruta
    Entonces esa parada tiene 2 reservas activas

  @SCRUM-283
  Escenario: Una parada sin reservas aparece con conteo cero
    Dado que existe la ruta de ejemplo con sus paradas
    Y que una parada de la ruta no tiene reservas vigentes
    Cuando se compone la información de la ruta
    Entonces esa parada tiene 0 reservas activas

  @SCRUM-283
  Escenario: No se puede componer el resumen de una ruta inexistente
    Cuando se intenta componer la información de una ruta que no existe
    Entonces se produce un error de recurso no encontrado
