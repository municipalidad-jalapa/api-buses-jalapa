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

  @HU-84 @CP-ETA-01
  Escenario: Sin velocidad reportada se deduce de las posiciones del GPS
    Dado que el vehiculo "BUS-01" reporta dos posiciones sin velocidad avanzando sobre el trazado
    Cuando consulto el ETA de la ruta 1
    Entonces el estado del bus es "EN_RUTA"
    Y las paradas pendientes son confiables

  @HU-84 @CP-ETA-03
  Escenario: Las paradas intermedias suman su espera, mayor si tienen reservas
    Dado que la parada de orden 2 tiene una reserva activa
    Y que el vehiculo "BUS-01" reporta posiciones recientes a 36 km/h antes de la parada 2
    Cuando consulto el ETA de la ruta 1
    Entonces los minutos a la parada de orden 3 incluyen la espera con reserva de la parada de orden 2

  @HU-84 @CP-ETA-05
  Escenario: Detenido fuera de parada por mucho tiempo no proyecta un numero optimista
    Dado que el vehiculo "BUS-01" lleva 6 minutos detenido fuera de cualquier parada
    Cuando consulto el ETA de la ruta 1
    Entonces el estado del bus es "DETENIDO_FUERA_DE_PARADA"
    Y todas las paradas tienen minutos nulos

  @HU-84 @CP-ETA-04
  Escenario: Una detencion breve fuera de parada no anula la estimacion
    Dado que el vehiculo "BUS-01" lleva 1 minutos detenido fuera de cualquier parada
    Cuando consulto el ETA de la ruta 1
    Entonces el estado del bus es "EN_RUTA"
    Y las paradas pendientes tienen minutos calculados

  @HU-84 @CP-ETA-10
  Escenario: En desvio el tiempo se recalcula por el camino que sigue el bus
    Dado que el vehiculo "BUS-01" sale del trazado a unos 300 metros de la 1a Calle
    Cuando consulto el ETA de la ruta 1
    Entonces el estado del bus es "EN_DESVIO"
    Y el desvio trae el punto de reincorporacion y el recorrido estimado
    Y el recorrido estimado empieza donde el bus dejo el trazado
    Y las paradas pendientes tienen minutos calculados
    Y las paradas pendientes no son confiables

  Escenario: Ruta inexistente
    Cuando consulto el ETA de la ruta 999999
    Entonces la respuesta tiene codigo 404
