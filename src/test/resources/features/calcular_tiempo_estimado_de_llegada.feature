# language: es
@SCRUM-166 @HU-71 @backend
Característica: Calcular el tiempo estimado de llegada

  Como sistema
  quiero calcular cuánto falta para que el bus de una ruta llegue a cada una de sus paradas pendientes
  para poder decirle al pasajero cuánto le falta esperar

  Antecedentes:
    Dado que la ruta 1 tiene trazado y paradas
    Y que el vehiculo "BUS-01" esta asignado a la ruta 1

  @criterio-1
  Escenario: La distancia sigue el recorrido y no la línea recta
    Dado que el vehiculo "BUS-01" reporta posiciones recientes a 30 km/h antes de la parada 2
    Cuando consulto el ETA de la ruta 1
    Entonces la respuesta tiene codigo 200
    Y la parada de orden 2 tiene menos minutos que la parada de orden 3
    Y la parada de orden 8 tiene más minutos que la parada de orden 5 aunque esté más cerca en línea recta

  @criterio-2
  Escenario: Usa la velocidad observada del vehiculo
    Dado que el vehiculo "BUS-01" reporta posiciones recientes a 30 km/h antes de la parada 2
    Cuando consulto el ETA de la ruta 1
    Entonces las paradas pendientes son confiables

  @criterio-2
  Escenario: Sin historial suficiente usa la velocidad promedio del recorrido
    Dado que el vehiculo "BUS-01" reporta una sola posicion reciente sin velocidad
    Cuando consulto el ETA de la ruta 1
    Entonces las paradas pendientes tienen minutos calculados
    Y las paradas pendientes no son confiables

  @criterio-3
  Escenario: El resultado se expresa en minutos para todas las paradas pendientes
    Dado que el vehiculo "BUS-01" reporta posiciones recientes a 30 km/h antes de la parada 2
    Cuando consulto el ETA de la ruta 1
    Entonces la respuesta incluye la ruta 1, el vehiculo y la fecha de calculo
    Y las 8 paradas del circuito traen paradaId, orden y minutos enteros

  @criterio-4
  Escenario: Se recalcula al llegar una posicion nueva respetando el limite de frecuencia
    Dado que el intervalo minimo de recalculo es de 10 segundos
    Y que el vehiculo "BUS-01" reporta posiciones recientes a 30 km/h antes de la parada 2
    Y que el ETA de la ruta 1 ya se calculo
    Cuando llega una posicion nueva en la parada 3 a los 9 segundos
    Entonces el ETA no se recalcula
    Cuando se vuelve a evaluar a los 10 segundos
    Entonces el ETA se recalcula con la nueva posicion

  @criterio-5
  Escenario: Posicion vieja no inventa un numero
    Dado que la ultima posicion del vehiculo "BUS-01" tiene mas de 120 segundos
    Cuando consulto el ETA de la ruta 1
    Entonces todas las paradas tienen minutos nulos
    Y ninguna parada es confiable

  @criterio-6
  Escenario: Dos rutas se calculan a la vez de forma independiente
    Dado que existe una segunda ruta con trazado y paradas
    Y que el vehiculo "BUS-02" esta asignado a la segunda ruta
    Y que el vehiculo "BUS-01" reporta posiciones recientes a 30 km/h antes de la parada 2
    Y que el vehiculo "BUS-02" reporta posiciones recientes en la parada 2 de la segunda ruta
    Cuando consulto el ETA de la ruta 1 y de la segunda ruta
    Entonces cada respuesta usa el vehiculo de su propia ruta
    Y cada respuesta trae solo las paradas de su propia ruta

  @criterio-7
  Escenario: No se consume ningun servicio externo
    Entonces el calculo del ETA no usa ningun cliente HTTP

  Escenario: Ruta inexistente
    Cuando consulto el ETA de la ruta 999999
    Entonces la respuesta tiene codigo 404
