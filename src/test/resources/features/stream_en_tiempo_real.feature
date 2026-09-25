# language: es
#
# Recordatorio del locale: con "language: es" Cucumber lee {double} en espanol,
# donde el punto separa MILES. Por eso aqui no van coordenadas.
@SCRUM-140 @HU-45 @backend
Característica: Recibir la posición del bus en tiempo real

  Como app del pasajero
  quiero un canal que me empuje la posición apenas llega
  para mover el marcador sin estar preguntando cada pocos segundos

  @criterio-3
  Escenario: El stream es público y no pide credencial
    Cuando un pasajero se conecta al stream de posiciones
    Entonces el stream queda abierto
    Y el stream pide al proxy que no acumule la respuesta

  @criterio-1
  Escenario: El stream emite Server-Sent Events
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y un pasajero conectado al stream
    Cuando el equipo reporta su posición
    Entonces el pasajero recibe un evento de posición

  @criterio-2
  Escenario: La posición recién ingestada llega al pasajero
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y un pasajero conectado al stream
    Cuando el equipo reporta su posición
    Entonces el pasajero recibe la posición del "BUS-01"

  @criterio-2
  Escenario: La misma posición llega a todos los suscriptores
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y dos pasajeros conectados al stream
    Cuando el equipo reporta su posición
    Entonces los dos pasajeros reciben la posición

  @criterio-2
  Escenario: Quien se conecta después ve de inmediato la posición vigente
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y el equipo ya reportó su posición correctamente
    Cuando un pasajero se conecta al stream de posiciones
    Entonces el pasajero recibe un evento de posición

  @criterio-2
  Escenario: Una lectura descartada por reloj desfasado no se difunde
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y un pasajero conectado al stream
    Cuando el equipo reporta una posición con el reloj de hace dos días
    Entonces el pasajero no recibe ningún evento de posición
