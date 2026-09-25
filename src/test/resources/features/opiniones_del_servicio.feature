# language: es
@SCRUM-26 @HU-146 @bloque-A @backend
Característica: Opiniones del servicio

  Como pasajero
  quiero opinar sobre el servicio desde la aplicación
  para que la municipalidad sepa cómo funciona cada ruta y cada unidad

  @A1-criterio-1 @A1-criterio-2 @A1-criterio-4
  Escenario: Un pasajero sin cuenta opina y el servidor resuelve el bus de la ruta
    Cuando el navegador "nav-bdd" opina con 4 estrellas sobre la ruta 1
    Entonces la respuesta tiene codigo 201
    Y la opinión queda atribuida al navegador "nav-bdd" y al bus "BUS-01"

  @A1-criterio-3
  Escenario: Una opinión sin texto ni calificación se rechaza
    Cuando el navegador "nav-bdd" envía una opinión vacía sobre la ruta 1
    Entonces la respuesta tiene codigo 422

  @A1-criterio-6
  Escenario: Al exceder el límite de envíos se responde 429
    Dado que el navegador "nav-bdd" ya envió 5 opiniones
    Cuando el navegador "nav-bdd" opina con 4 estrellas sobre la ruta 1
    Entonces la respuesta tiene codigo 429

  @A1-criterio-7 @A3-criterio-5
  Escenario: El texto se guarda tal cual y el panel lo muestra neutralizado
    Cuando el navegador "nav-bdd" comenta "<b>hola</b>" sobre la ruta 1
    Entonces el texto guardado es "<b>hola</b>"
    Y el panel municipal lo devuelve como "&lt;b&gt;hola&lt;/b&gt;"

  @A3-criterio-1 @A3-criterio-2
  Escenario: El panel filtra las opiniones por tipo
    Dado que hay una queja y un comentario sobre la ruta 1
    Cuando el administrador lista las opiniones de tipo "queja"
    Entonces el panel muestra 1 opinión

  @A3-criterio-3
  Escenario: El administrador marca una opinión como atendida
    Dado que hay una queja y un comentario sobre la ruta 1
    Cuando el administrador marca la queja como atendida
    Entonces la respuesta tiene codigo 200
    Y queda registrado quién la atendió y cuándo

  @A3-criterio-4
  Escenario: El panel muestra el promedio de la ruta
    Dado que la ruta 1 tiene calificaciones de 5 y 3 estrellas
    Cuando el administrador lista las opiniones de la ruta 1
    Entonces el promedio de la ruta es "4.0"

  @A3-criterio-6
  Escenario: Un conductor no puede ver las opiniones
    Cuando un conductor con su sesión lista las opiniones
    Entonces la respuesta tiene codigo 403

  @A1-criterio-8
  Escenario: La tabla de opiniones tiene índices por ruta, vehículo y fecha
    Entonces la tabla de opiniones tiene los índices "idx_opinion_ruta", "idx_opinion_vehiculo" y "idx_opinion_fecha"
