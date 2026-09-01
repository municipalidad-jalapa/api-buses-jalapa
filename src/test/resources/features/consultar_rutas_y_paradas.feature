# language: es
@SCRUM-130 @HU-35 @backend
Característica: Consultar rutas y paradas

  Como pasajero
  quiero ver las rutas del bus y sus paradas
  para saber por dónde pasa y dónde puedo esperarlo

  @criterio-1
  Escenario: Se listan las rutas activas con sus paradas
    Cuando alguien consulta las rutas
    Entonces se devuelve la ruta activa con sus 8 paradas

  @criterio-2
  Escenario: Las paradas vienen en el orden del recorrido
    Cuando alguien consulta las rutas
    Entonces la primera parada del recorrido es "Parque Central"
    Y la última parada del recorrido es "Transito Rojas - Chipilapa"

  @criterio-2
  Escenario: Las coordenadas se exponen con nombre y sin invertirse
    Cuando alguien consulta las rutas
    Entonces cada parada trae su latitud y su longitud por nombre
    Y las coordenadas caen dentro de Jalapa

  @criterio-2
  Escenario: La ruta trae el recorrido dibujado sobre las calles
    Cuando alguien consulta las rutas
    Entonces la ruta trae un trazado con más puntos que paradas
    Y el trazado empieza y termina en el mismo punto, porque es un circuito

  @criterio-3
  Escenario: El pasajero anónimo puede consultar sin credencial
    Cuando alguien consulta las rutas sin ninguna credencial
    Entonces la consulta se acepta
