# language: es
@SCRUM-26 @HU-146 @bloque-E @backend
Característica: El piloto marca abordajes y avisa un atraso

  Como piloto
  quiero registrar quién abordó y avisar cuando vengo demorado
  para que el conteo sea el real y el pasajero sepa por qué el bus tarda más de lo estimado

  @E-criterio-1
  Escenario: El dato del piloto prevalece sobre la confirmación del pasajero
    Dado que una reserva de la ruta del piloto está activa
    Y que el pasajero confirmó que sí abordó
    Cuando el piloto registra que no abordó
    Entonces la reserva queda marcada por el conductor
    Y esa reserva queda en estado "CANCELADA"

  @E-criterio-2
  Escenario: El piloto avisa un atraso con motivo y demora estimada
    Cuando el piloto reporta un atraso por "trafico" de 10 minutos
    Entonces la respuesta tiene codigo 201
    Y el atraso queda registrado en la ruta del piloto

  @E-criterio-2
  Escenario: Un motivo que no existe no se registra
    Cuando el piloto reporta un atraso por "lluvia" de 10 minutos
    Entonces la respuesta tiene codigo 422

  @E-criterio-3
  Escenario: El aviso llega a la pantalla del pasajero junto al tiempo estimado
    Dado que el piloto reportó un atraso por "incidente" de 20 minutos
    Cuando consulto el ETA de la ruta del piloto
    Entonces el tiempo estimado viene acompañado del aviso de demora
    Y el aviso dice el motivo "INCIDENTE" y 20 minutos

  @E-criterio-3
  Escenario: Retirado el aviso, el pasajero deja de verlo
    Dado que el piloto reportó un atraso por "trafico" de 15 minutos
    Cuando el piloto retira el aviso
    Y consulto el ETA de la ruta del piloto
    Entonces el tiempo estimado no trae ningún aviso de demora
