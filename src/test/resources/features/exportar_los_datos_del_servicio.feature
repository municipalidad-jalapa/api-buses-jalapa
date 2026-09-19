# language: es
@HU-86 @backend
Característica: Exportar los datos del servicio

  Como administrador municipal
  quiero descargar los datos de demanda y recorridos
  para usarlos en informes propios de la Municipalidad

  @criterio-1
  Escenario: La exportación es una hoja de cálculo
    Dado que el dispositivo "5b9f3c1e-7a44-4d0e-9d55-0a1b2c3d4e5f" reservó el "2026-09-10"
    Cuando el administrador exporta los datos del servicio desde "2026-09-01" hasta "2026-09-15"
    Entonces la respuesta tiene codigo 200
    Y recibe una hoja de cálculo con las hojas "Resumen", "Demanda" y "Recorridos"
    Y la hoja "Demanda" tiene 1 fila de datos

  @criterio-2
  Escenario: El rango de fechas limita lo que se exporta
    Dado que el dispositivo "dispositivo-del-dia-10" reservó el "2026-09-10"
    Y que el dispositivo "dispositivo-del-dia-20" reservó el "2026-09-20"
    Cuando el administrador exporta los datos del servicio desde "2026-09-16" hasta "2026-09-30"
    Entonces la respuesta tiene codigo 200
    Y la hoja "Demanda" tiene 1 fila de datos
    Y la hoja "Demanda" muestra la fecha "2026-09-20"

  @criterio-2
  Escenario: Un rango con la fecha final antes de la inicial se rechaza
    Cuando el administrador exporta los datos del servicio desde "2026-09-15" hasta "2026-09-01"
    Entonces la respuesta tiene codigo 422

  @criterio-3
  Escenario: La exportación no expone datos que identifiquen a un pasajero
    Dado que el dispositivo "5b9f3c1e-7a44-4d0e-9d55-0a1b2c3d4e5f" reservó el "2026-09-10"
    Y que el dispositivo "c0ffee00-1234-4abc-8def-abcdefabcdef" reservó el "2026-09-10"
    Cuando el administrador exporta los datos del servicio desde "2026-09-01" hasta "2026-09-15"
    Entonces la respuesta tiene codigo 200
    Y el archivo no contiene el identificador "5b9f3c1e-7a44-4d0e-9d55-0a1b2c3d4e5f"
    Y el archivo no contiene el identificador "c0ffee00-1234-4abc-8def-abcdefabcdef"
    Y la hoja "Demanda" tiene 1 fila de datos

  @acceso
  Escenario: Un pasajero sin sesión no puede exportar
    Cuando un pasajero sin sesión intenta exportar los datos del servicio
    Entonces la respuesta tiene codigo 401
