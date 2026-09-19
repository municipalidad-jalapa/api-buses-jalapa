# language: es
@SCRUM-26 @HU-146 @bloque-B @backend
Característica: Sesión opcional del pasajero

  Como pasajero
  quiero poder entrar con mi cuenta de Google o seguir como invitado
  para conservar mis reservas y opiniones sin perder nada de lo que ya funciona sin cuenta

  @B2-criterio-1
  Escenario: Con una cuenta de Google se obtiene la sesión propia con rol de pasajero
    Cuando la cuenta de Google "uid-bdd-ana" inicia sesión como pasajero
    Entonces recibe una sesión del sistema con rol "pasajero"

  @B2-criterio-2 @B2-criterio-3
  Escenario: Las reservas y opiniones del navegador quedan en la cuenta, una sola vez
    Dado que el navegador "nav-bdd" tiene 2 reservas y 1 opinión como invitado
    Y que la cuenta de Google "uid-bdd-ana" inició sesión como pasajero
    Cuando vincula el navegador "nav-bdd"
    Entonces se vincularon 2 reservas y 1 opinión
    Cuando vincula el navegador "nav-bdd"
    Entonces se vincularon 0 reservas y 0 opinión

  @B2-criterio-5
  Escenario: El rol de pasajero no entra al panel del conductor
    Dado que la cuenta de Google "uid-bdd-ana" inició sesión como pasajero
    Cuando el pasajero consulta el panel del conductor
    Entonces la respuesta tiene codigo 403

  @B2-criterio-5
  Escenario: El rol de pasajero no entra al panel municipal
    Dado que la cuenta de Google "uid-bdd-ana" inició sesión como pasajero
    Cuando el pasajero consulta el panel municipal
    Entonces la respuesta tiene codigo 403

  @B2-criterio-6
  Escenario: La cuenta es opcional en reservas y opiniones
    Entonces las reservas y las opiniones aceptan no tener cuenta
