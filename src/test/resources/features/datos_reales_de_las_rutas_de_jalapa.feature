# language: es
@SCRUM-136 @HU-41 @backend @catalogo @postgis
Característica: Datos reales de las rutas de Jalapa

  Como pasajero
  quiero que las paradas y el trazado correspondan a la ruta real
  para que el mapa y los tiempos me sirvan de verdad

  Escenario: La ruta principal utiliza las paradas levantadas en campo
    Cuando alguien consulta las rutas disponibles
    Entonces la ruta "RUTA PRINCIPAL" contiene 8 paradas ordenadas
    Y sus paradas se llaman desde "Parada 1" hasta "Parada 8"

  Escenario: El recorrido principal es un circuito real
    Cuando alguien consulta la ruta "RUTA PRINCIPAL"
    Entonces su trazado tiene más vértices que paradas
    Y el trazado inicia y termina en el punto de origen
    Y no existe una novena parada duplicada para cerrar el circuito

  Escenario: Las coordenadas geoespaciales no están invertidas
    Cuando se inspeccionan las coordenadas almacenadas de las rutas
    Entonces las latitudes y longitudes corresponden a Jalapa
    Y las geometrías utilizan el SRID 4326

  Escenario: La ruta secundaria conserva únicamente el tramo disponible
    Cuando alguien consulta la ruta "RUTA SECUNDARIA"
    Entonces contiene 6 paradas ordenadas
    Y su trazado parcial no se cierra artificialmente

  Escenario: Cada ruta utiliza su propio vehículo
    Dado que existen las dos rutas activas
    Entonces "RUTA PRINCIPAL" está vinculada con "BUS-01"
    Y "RUTA SECUNDARIA" está vinculada con "BUS-02"

  Escenario: El catálogo trata ambas rutas con el mismo contrato
    Cuando alguien consulta las rutas disponibles
    Entonces aparecen "RUTA PRINCIPAL" y "RUTA SECUNDARIA"
    Y ambas incluyen nombre, trazado y paradas ordenadas
