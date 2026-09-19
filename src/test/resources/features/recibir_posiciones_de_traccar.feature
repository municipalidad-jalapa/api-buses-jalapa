# language: es
@SCRUM-24 @HU-144 @backend
Característica: Recibir en la API las posiciones que reenvía Traccar

  Como sistema
  quiero un módulo que reciba el reenvío de Traccar y registre la posición del bus
  para que el GPS real alimente la telemetría sin romper lo que ya funciona

  Antecedentes:
    Dado que el dispositivo Traccar "860000000000001" está asociado al equipo del bus "BUS-01"

  @criterio-1
  Escenario: Sin la cabecera de integración se responde 401
    Cuando Traccar reenvía una posición del dispositivo "860000000000001" sin la cabecera de integración
    Entonces la respuesta tiene codigo 401
    Y no se registró ninguna posición

  @criterio-1
  Escenario: Con una cabecera de integración que no coincide se responde 401
    Cuando Traccar reenvía una posición del dispositivo "860000000000001" con el token "token-equivocado-de-mas-de-32-caracteres"
    Entonces la respuesta tiene codigo 401

  @criterio-2
  Escenario: La posición se atribuye al vehículo del equipo asociado
    Cuando Traccar reenvía la posición 501 del dispositivo "860000000000001"
    Entonces la respuesta tiene codigo 202
    Y la posición vigente es del bus "BUS-01"

  @criterio-2
  Escenario: Un dispositivo no asociado responde 422 y no registra nada
    Cuando Traccar reenvía la posición 502 del dispositivo "000000000000000"
    Entonces la respuesta tiene codigo 422
    Y no se registró ninguna posición

  @criterio-3
  Escenario: Una latitud fuera de rango se rechaza
    Cuando Traccar reenvía una posición del dispositivo "860000000000001" con latitud 95
    Entonces la respuesta tiene codigo 422
    Y no se registró ninguna posición

  @criterio-3
  Escenario: La velocidad en nudos se guarda en kilómetros por hora
    Cuando Traccar reenvía la posición 503 del dispositivo "860000000000001" a 10 nudos
    Entonces la velocidad registrada es 18.52 km/h

  @criterio-4
  Escenario: Una lectura fuera de la ventana de doce horas se descarta
    Cuando Traccar reenvía la posición 504 del dispositivo "860000000000001" con fecha de hace 13 horas
    Entonces la respuesta resume 1 recibidas, 0 aceptadas y 1 descartadas

  @criterio-5 @criterio-7
  Escenario: Un reenvío repetido no duplica la posición
    Cuando Traccar reenvía la posición 505 del dispositivo "860000000000001"
    Y Traccar reenvía la posición 505 del dispositivo "860000000000001"
    Entonces la respuesta resume 1 recibidas, 0 aceptadas y 1 descartadas
    Y hay 1 posición registrada

  @criterio-6
  Escenario: El registro no reenvía por HTTP al propio backend
    Entonces el módulo de Traccar registra con el servicio de telemetría y sin clientes HTTP

  @criterio-8
  Escenario: La base de datos impide posiciones duplicadas por clave de origen
    Entonces existe la relación entre dispositivo externo y equipo
    Y existe la restricción única de la clave de origen
