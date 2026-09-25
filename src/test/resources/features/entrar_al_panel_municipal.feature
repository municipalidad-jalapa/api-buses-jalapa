# language: es
@SCRUM-173 @HU-78 @backend
Característica: Entrar al panel web municipal

  Como administrador municipal
  quiero entrar a un panel desde la computadora de la oficina
  para supervisar el servicio sin instalar nada

  Antecedentes:
    Dado que existe una cuenta de administrador con uid "uid-muni-bdd"
    Y que existe una cuenta de conductor con uid "uid-cond-bdd"

  @criterio-1
  Escenario: La autenticación reutiliza el mecanismo existente con rol de administrador
    Cuando la cuenta con uid "uid-muni-bdd" inicia sesión en el panel
    Entonces recibe un JWT del backend con rol "admin"

  @criterio-2
  Escenario: El panel se usa desde el navegador con la API pública de login
    Cuando se consulta el login del panel sin credenciales
    Entonces la respuesta tiene codigo 400

  @criterio-3
  Escenario: La sesión se cierra por inactividad con plazo configurable
    Dado que el plazo de inactividad configurado es de 30 minutos
    Cuando la cuenta con uid "uid-muni-bdd" inicia sesión en el panel
    Entonces la sesión vence en 30 minutos si no se renueva
    Cuando el administrador usa una sesión vencida por inactividad
    Entonces la respuesta tiene codigo 401

  @criterio-4
  Escenario: Un conductor que intenta entrar recibe 403
    Cuando la cuenta con uid "uid-cond-bdd" intenta iniciar sesión en el panel
    Entonces se le niega el acceso al panel
    Cuando un conductor con su sesión consulta el panel municipal
    Entonces la respuesta tiene codigo 403

  @criterio-4
  Escenario: Un pasajero anónimo que intenta entrar no pasa
    Cuando un pasajero sin sesión consulta el panel municipal
    Entonces la respuesta tiene codigo 401

  @criterio-5
  Escenario: La cuenta de administrador no guarda contraseña en el sistema
    Entonces la cuenta con uid "uid-muni-bdd" no tiene contraseña almacenada

  @criterio-6
  Escenario: El administrador ve el servicio completo, no una sola ruta
    Cuando el administrador con su sesión consulta el panel municipal
    Entonces la respuesta tiene codigo 200
    Y el panel muestra todas las rutas activas
