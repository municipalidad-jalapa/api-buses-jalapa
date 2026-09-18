# Carpeta `test/` — HU Desarrollo-135

Bundle de pruebas de la HU **"Vigencia de cinco minutos con renovación y expiración"**.

| Archivo | Qué es |
|---|---|
| [`MANUAL_DE_PRUEBAS.md`](MANUAL_DE_PRUEBAS.md) | Cómo ejecutar **todas** las pruebas: automáticas (`mvn test`) y la manual end-to-end. Incluye requisitos de entorno, comandos clase por clase, trazabilidad criterio→prueba y solución de problemas. |
| [`RESULTADOS.txt`](RESULTADOS.txt) | Salida real de la última ejecución: suite completa (136/136 OK) + prueba manual con `curl` (11/11 pasos OK). |
| [`prueba-manual.sh`](prueba-manual.sh) | Script de la prueba manual end-to-end (`bash test/prueba-manual.sh` con la app y la BD arriba). |
| [`casos/`](casos/) | **Copias para lectura** de los archivos de prueba nuevos. |

## Importante sobre `casos/`

Son **copias**. Maven ejecuta los originales, que tienen que vivir bajo `src/test/java/`:

| Copia en `casos/` | Original que corre Maven |
|---|---|
| `ReservaTest.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/dominio/ReservaTest.java` |
| `DemandaServiceTest.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/servicio/DemandaServiceTest.java` |
| `ReservaVigenciaIT.java` | `src/test/java/gt/muni/jalapa/ecoruta/demanda/ReservaVigenciaIT.java` |

No borres los originales: si esas clases salen de `src/test/java/` dejan de ejecutarse.
Si editás una prueba, hacelo en el original y volvé a copiarla aquí.

## Arranque rápido

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"   # JDK 21 obligatorio
docker info                                                                  # Docker Desktop debe estar arriba

# todas las pruebas automáticas
mvn test

# solo las de esta HU
mvn test -Dtest='ReservaTest,DemandaServiceTest,ReservaVigenciaIT,EsquemaValidaTest'
```
