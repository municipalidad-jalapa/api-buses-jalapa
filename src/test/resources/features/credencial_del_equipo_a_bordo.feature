# language: es
#
# OJO al escribir numeros aqui: con "language: es" Cucumber interpreta {double}
# en locale espanol, donde el punto es separador de MILES. Escribir 14.6335 da
# 146335. Por eso los escenarios no llevan coordenadas: el detalle tecnico vive
# en las pruebas JUnit, y aqui solo va lo que un product owner puede leer.
@SCRUM-142 @HU-47 @backend
Característica: Autenticar al equipo a bordo con credencial propia

  Como administrador del sistema
  quiero que el módulo se autentique con su propia credencial
  para no depender de la sesión de una persona y poder revocar un solo equipo

  @criterio-a
  Escenario: La credencial del equipo no depende de ninguna cuenta de persona
    Cuando el administrador da de alta el equipo "Tableta cabina 1"
    Entonces se emite una credencial con el formato del equipo a bordo
    Y la credencial no está ligada a ninguna cuenta de usuario

  @criterio-a
  Escenario: La credencial se muestra una sola vez
    Cuando el administrador da de alta el equipo "Tableta cabina 1"
    Entonces el listado de equipos no vuelve a mostrar el secreto

  @criterio-b
  Escenario: La ingesta acepta la credencial del equipo
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Cuando el equipo reporta su posición
    Entonces la ingesta responde 202
    Y la posición reportada queda registrada a nombre de ese equipo

  @criterio-b
  Escenario: Sin credencial la ingesta se rechaza
    Cuando alguien reporta una posición sin credencial
    Entonces la ingesta responde 401
    Y el cuerpo del error tiene el formato uniforme de la API
    Y no se guardó ninguna posición

  @criterio-b
  Esquema del escenario: Una credencial que no sirve se rechaza sin reventar
    Cuando alguien reporta una posición con la credencial <credencial>
    Entonces la ingesta responde 401
    Y no se guardó ninguna posición

    Ejemplos:
      | credencial          | caso                                  |
      | "esto.no.es.un.jwt" | basura con forma de token             |
      | "eq_corta.abc"      | prefijo correcto pero mal formada     |
      | "de-otro-sistema"   | un bearer que no es nuestro           |

  @criterio-c
  Escenario: Una credencial revocada deja de ser aceptada de inmediato
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Y el equipo ya reportó su posición correctamente
    Cuando el administrador revoca la credencial del equipo
    Y el equipo vuelve a reportar con la misma credencial
    Entonces la ingesta responde 401
    Y no se guardó ninguna posición nueva

  @criterio-c
  Escenario: Revocar un equipo no corta a los demás
    Dado un equipo "Tableta del bus 1" con credencial vigente
    Y otro equipo "Tableta del bus 2" con credencial vigente
    Cuando el administrador revoca la credencial del primer equipo
    Entonces el primer equipo ya no puede reportar
    Pero el segundo equipo sigue reportando con normalidad

  @criterio-d
  Escenario: El secreto no queda escrito en los registros
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Cuando el equipo reporta su posición
    Y alguien intenta reportar con esa credencial adulterada
    Entonces el secreto no aparece en ninguna línea de registro
    Pero el código público del equipo sí aparece

  @criterio-d
  Escenario: El secreto no sirve si viaja en la URL
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Cuando el equipo reporta su posición mandando la credencial en la URL
    Entonces la ingesta responde 401
    Y el secreto no aparece en la respuesta
    Y no se guardó ninguna posición
