# Carpeta `test/` — HU Desarrollo-135 y Desarrollo-95

Bundle de pruebas para las HU **"Vigencia de cinco minutos con renovación y expiración"** y **"Endurecer la seguridad de la API"**.

Incluye: ciclo de vida de la reserva, límite de peticiones por dispositivo y por IP en los endpoints públicos, CORS restringido a orígenes conocidos, cabeceras de seguridad, y la regla que evita registrar demanda a un ritmo imposible para una persona.

| Archivo | Qué es |
|---|---|
| [`MANUAL_DE_PRUEBAS.md`](MANUAL_DE_PRUEBAS.md) | Cómo ejecutar **todas** las pruebas: automáticas (`mvn test`) y las manuales end-to-end (simuladas con `curl`). Incluye trazabilidad criterio→prueba y solución de problemas. |
| [`RESULTADOS.txt`](RESULTADOS.txt) | Salida real de la última ejecución: suite completa + pruebas manuales. |
| [`prueba-manual.sh`](prueba-manual.sh) | Script de las pruebas manuales end-to-end (`bash test/prueba-manual.sh` con la app y la BD arriba). |
| [`casos/`](casos/) | **Copias para lectura** de los archivos de prueba nuevos o modificados. |

## Importante sobre `casos/`

Son **copias**. Maven ejecuta los originales, que tienen que vivir bajo `src/test/java/`:

| Copia en `casos/` | Original que corre Maven | Qué prueba |
|---|---|---|
| `ReservaTest.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/dominio/ReservaTest.java` | Reglas de dominio de `Reserva`: vigencia, renovación, expiración. |
| `DemandaServiceTest.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/servicio/DemandaServiceTest.java` | Servicio con mocks: cálculo de vigencia y barrido. |
| `ReservaVigenciaIT.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/ReservaVigenciaIT.java` | HU-135: Pruebas de integración sobre vigencia de reservas. |
| `LimitadorDeVentanaFijaTest.java` | `src/test/java/.../seguridad/ratelimit/LimitadorDeVentanaFijaTest.java` | El contador de ventana fija en memoria, sin Spring. |
| `RateLimitFilterTest.java` | `src/test/java/.../seguridad/ratelimit/RateLimitFilterTest.java` | El filtro de límite de peticiones aislado, sin Spring. |
| `RateLimitPorIpIT.java` | `src/test/java/.../seguridad/RateLimitPorIpIT.java` | Límite por IP contra la app real. |
| `RateLimitPorDispositivoIT.java` | `src/test/java/.../seguridad/RateLimitPorDispositivoIT.java` | Límite por dispositivo contra la app real. |
| `SecurityHeadersIT.java` | `src/test/java/.../seguridad/SecurityHeadersIT.java` | Cabeceras de seguridad contra la app real. |
| `ReservaServiceTest.java` | `src/test/java/.../demanda/servicio/ReservaServiceTest.java` | Regla del ritmo mínimo entre reservas (dominio, con mocks). |
| `CrearReservaIT.java` | `src/test/java/.../demanda/CrearReservaIT.java` | Regla del ritmo mínimo contra la app real. |

No borres los originales: si esas clases salen de `src/test/java/` dejan de ejecutarse. Si editás una prueba, hacelo en el original y volvé a copiarla aquí.

## Arranque rápido

```bash
export JAVA_HOME="/ruta/a/un/JDK/21+"     # ver MANUAL_DE_PRUEBAS.md, sección 1
docker info                                # Docker Desktop debe estar arriba

# todas las pruebas automáticas
mvn test

# solo las de esta integración
mvn test -Dtest='ReservaTest,DemandaServiceTest,ReservaVigenciaIT,EsquemaValidaTest,LimitadorDeVentanaFijaTest,RateLimitFilterTest,RateLimitPorIpIT,RateLimitPorDispositivoIT,SecurityHeadersIT,ReservaServiceTest,ReservaRenovacionTest,ReservaCancelacionTest,CrearReservaIT,CorsIT'