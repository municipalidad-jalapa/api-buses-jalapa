# Carpeta `test/` — HU Desarrollo-95

Bundle de pruebas de la HU **"Endurecer la seguridad de la API"** (D4 + O4): límite
de peticiones por dispositivo y por IP en los endpoints públicos, CORS restringido a
orígenes conocidos, cabeceras de seguridad, y que un dispositivo no pueda registrar
demanda a un ritmo imposible para una persona.

| Archivo | Qué es |
|---|---|
| [`MANUAL_DE_PRUEBAS.md`](MANUAL_DE_PRUEBAS.md) | Cómo ejecutar **todas** las pruebas: automáticas (`mvn test`) y las manuales end-to-end (con la API real, y simuladas con `curl` para no depender del tráfico de producción). Incluye trazabilidad criterio→prueba y solución de problemas. |
| [`RESULTADOS.txt`](RESULTADOS.txt) | Salida real de la última ejecución: suite completa + pruebas manuales, con qué pasó y qué no. |
| [`prueba-manual.sh`](prueba-manual.sh) | Script de las pruebas manuales end-to-end (`bash test/prueba-manual.sh` con la app y la BD arriba). |
| [`casos/`](casos/) | **Copias para lectura** de los archivos de prueba nuevos o modificados por esta HU. |

## Importante sobre `casos/`

Son **copias**. Maven ejecuta los originales, que tienen que vivir bajo `src/test/java/`:

| Copia en `casos/` | Original que corre Maven | Qué prueba |
|---|---|---|
| `LimitadorDeVentanaFijaTest.java` | `src/test/java/.../seguridad/ratelimit/LimitadorDeVentanaFijaTest.java` | El contador de ventana fija en memoria, sin Spring. |
| `RateLimitFilterTest.java` | `src/test/java/.../seguridad/ratelimit/RateLimitFilterTest.java` | El filtro de límite de peticiones aislado, sin Spring. |
| `RateLimitPorIpIT.java` | `src/test/java/.../seguridad/RateLimitPorIpIT.java` | Límite por IP contra la app real. |
| `RateLimitPorDispositivoIT.java` | `src/test/java/.../seguridad/RateLimitPorDispositivoIT.java` | Límite por dispositivo contra la app real. |
| `SecurityHeadersIT.java` | `src/test/java/.../seguridad/SecurityHeadersIT.java` | Cabeceras de seguridad contra la app real. |
| `ReservaServiceTest.java` | `src/test/java/.../demanda/servicio/ReservaServiceTest.java` | Regla del ritmo mínimo entre reservas (dominio, con mocks). |
| `CrearReservaIT.java` | `src/test/java/.../demanda/CrearReservaIT.java` | Regla del ritmo mínimo contra la app real. |
| `ReservaVigenciaIT.java` | `src/test/java/.../demanda/ReservaVigenciaIT.java` | HU-135, preexistente; se le ajustó un caso (`una_reserva_expirada_no_cuenta_como_activa_ni_impide_una_nueva`) para no chocar con la regla nueva del ritmo mínimo — ver `RESULTADOS.txt`. |

`ReservaRenovacionTest.java` y `ReservaCancelacionTest.java` solo cambiaron en una
línea (el nuevo parámetro de `DemandaProperties`); no se copian aquí porque no traen
casos nuevos de esta HU. `CorsIT.java` (SCRUM-274) ya existía y no cambió: el
criterio "CORS restringido a orígenes conocidos" ya estaba implementado antes de
esta HU y solo se verificó que sigue vigente.

No borres los originales: si esas clases salen de `src/test/java/` dejan de ejecutarse.
Si editás una prueba, hacelo en el original y volvé a copiarla aquí.

## Arranque rápido

```bash
export JAVA_HOME="/ruta/a/un/JDK/21+"     # ver MANUAL_DE_PRUEBAS.md, sección 1
docker info                                # Docker Desktop debe estar arriba

# todas las pruebas automáticas
mvn test

# solo las de esta HU
mvn test -Dtest='LimitadorDeVentanaFijaTest,RateLimitFilterTest,RateLimitPorIpIT,RateLimitPorDispositivoIT,SecurityHeadersIT,ReservaServiceTest,ReservaRenovacionTest,ReservaCancelacionTest,CrearReservaIT,ReservaVigenciaIT,CorsIT'
```
