# language: es
@SCRUM-26 @HU-146 @bloque-F @backend
Característica: Métricas del panel municipal

  Como municipalidad
  quiero ver cuántos pasajeros suben y cómo califican el servicio
  para decidir con datos y no con impresiones

  @F-criterio-1
  Escenario: Cuenta los pasajeros que subieron, por ruta y por periodo
    Dado que el piloto marcó 3 abordajes en la ruta 1
    Y que el piloto marcó 1 abordaje en la ruta 2
    Cuando consulto los abordajes del panel
    Entonces el total de pasajeros subidos es 4
    Y el conteo viene desglosado por ruta, por vehículo y por periodo

  @F-criterio-1
  Escenario: Solo cuenta lo que marcó el piloto
    Dado que el piloto marcó 1 abordaje en la ruta 1
    Y que un pasajero dijo haber abordado sin que el piloto lo marcara
    Cuando consulto los abordajes del panel
    Entonces el total de pasajeros subidos es 1

  @F-criterio-2
  Escenario: La opinión acepta las tres valoraciones como campos opcionales
    Cuando opino la ruta 1 con calidad 4, limpieza 2 y conducción 5
    Entonces la respuesta tiene codigo 201
    Cuando opino la ruta 1 solo con la calificación general
    Entonces la respuesta tiene codigo 201

  @F-criterio-3
  Escenario: Cada dimensión se promedia por separado
    Dado que opiné la ruta 1 con calidad 4, limpieza 2 y conducción 5
    Y que opiné la ruta 1 con calidad 5, limpieza 3 y conducción 4
    Cuando consulto las opiniones del panel
    Entonces el promedio de calidad de la ruta 1 es 4.5
    Y el promedio de limpieza de la ruta 1 es 2.5
    Y el promedio de conducción de la ruta 1 es 4.5

  @F-criterio-3
  Escenario: Una dimensión que nadie puntuó no se muestra como cero
    Dado que opiné la ruta 1 con calidad 4, limpieza 0 y conducción 0
    Cuando consulto las opiniones del panel
    Entonces el promedio de limpieza de la ruta 1 no tiene dato
