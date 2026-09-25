# language: es
@SCRUM-26 @HU-146 @bloque-D @backend
Característica: Modelo de roles y permisos

  Como municipalidad
  quiero que cada rol pueda hacer solo lo suyo
  para que nadie administre el sistema ni vea información que no le toca

  @D-criterio-1 @D-criterio-5
  Escenario: Existen los cuatro roles y la cuenta inicial de SuperAdmin
    Entonces los roles de operación son "CONDUCTOR", "ADMIN" y "SUPERADMIN"
    Y el pasajero tiene su propio rol fuera de las cuentas de operación
    Y existe la cuenta inicial de SuperAdmin sin contraseña guardada

  @D-criterio-2 @D-criterio-3
  Escenario: Solo el SuperAdmin administra cuentas
    Dado que tengo una sesión de "superadmin"
    Cuando administro las cuentas
    Entonces la respuesta tiene codigo 200
    Dado que tengo una sesión de "municipalidad"
    Cuando administro las cuentas
    Entonces la respuesta tiene codigo 403
    Dado que tengo una sesión de "piloto"
    Cuando administro las cuentas
    Entonces la respuesta tiene codigo 403
    Dado que tengo una sesión de "pasajero"
    Cuando administro las cuentas
    Entonces la respuesta tiene codigo 403

  @D-criterio-2 @D-criterio-3
  Escenario: Solo el SuperAdmin administra rutas, paradas y vehículos
    Dado que tengo una sesión de "superadmin"
    Cuando administro los vehículos
    Entonces la respuesta tiene codigo 200
    Dado que tengo una sesión de "municipalidad"
    Cuando administro los vehículos
    Entonces la respuesta tiene codigo 403

  @D-criterio-3 @D-criterio-4
  Escenario: El panel municipal no es para el piloto ni para el pasajero
    Dado que tengo una sesión de "municipalidad"
    Cuando consulto el panel municipal como ese rol
    Entonces la respuesta tiene codigo 200
    Dado que tengo una sesión de "piloto"
    Cuando consulto el panel municipal como ese rol
    Entonces la respuesta tiene codigo 403
    Dado que tengo una sesión de "pasajero"
    Cuando consulto el panel municipal como ese rol
    Entonces la respuesta tiene codigo 403

  @D-criterio-4
  Escenario: El piloto se acota a su ruta asignada
    Dado que tengo una sesión de "piloto"
    Cuando marco como atendida una parada de otra ruta
    Entonces la respuesta tiene codigo 403

  @D-criterio-2
  Escenario: El sistema nunca se queda sin SuperAdmin
    Dado que tengo una sesión de "superadmin"
    Y que solo queda un SuperAdmin activo
    Cuando desactivo ese SuperAdmin
    Entonces la respuesta tiene codigo 422
