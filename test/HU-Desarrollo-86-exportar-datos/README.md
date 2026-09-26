# Carpeta `test/HU-Desarrollo-86-exportar-datos/` — HU Desarrollo-86

Bundle de pruebas de la HU **"Exportar los datos del servicio"**: el administrador municipal
descarga la demanda y los recorridos en una hoja de cálculo, con rango de fechas seleccionable y
sin exponer datos que identifiquen a un pasajero.

| Archivo | Qué es |
|---|---|
| [`MANUAL_DE_PRUEBAS.md`](MANUAL_DE_PRUEBAS.md) | Cómo ejecutar **todo**: automáticas (unitarias, integración, Gherkin), con datos **simulados** y **reales**, y las manuales end-to-end. Incluye trazabilidad criterio → prueba y solución de problemas. |
| [`RESULTADOS.txt`](RESULTADOS.txt) | **Estado de todas las pruebas** del backend (veredicto global, tabla por clase, detalle de cada prueba PASS/FAIL) y la salida de las pruebas manuales. |
| [`prueba-manual.sh`](prueba-manual.sh) | Prueba manual end-to-end contra la app real: `MODO=simulado` (genera sus datos) o `MODO=real` (usa los del entorno). |
| [`leer_xlsx.py`](leer_xlsx.py) | Lee el `.xlsx` sin instalar nada (solo biblioteca estándar): hojas, filas, resumen y todo el texto del ZIP. Lo usa `prueba-manual.sh`. |
| [`generar-resultados.ps1`](generar-resultados.ps1) | Regenera `RESULTADOS.txt` a partir de los informes de `mvn test`. |
| [`casos/`](casos/) | **Copias para lectura** de las pruebas nuevas o modificadas por esta HU. |

## Importante sobre `casos/`

Son **copias**. Maven ejecuta los originales, que tienen que vivir bajo `src/test/`:

| Copia en `casos/` | Original que corre Maven | Qué prueba |
|---|---|---|
| `LibroDelServicioTest.java` | `src/test/java/.../exportacion/servicio/` | El `.xlsx` armado y leído de vuelta (sin Spring ni Docker). |
| `ExportacionServiceTest.java` | `src/test/java/.../exportacion/servicio/` | Reglas del rango de fechas (sin Spring ni Docker). |
| `ExportacionServicioIT.java` | `src/test/java/.../exportacion/` | El endpoint contra PostGIS: criterios 1, 2 y 3, errores y acceso. |
| `ExportacionConRutaRealIT.java` | `src/test/java/.../exportacion/` | **Datos reales**: las calles de las rutas sembradas, contra el largo medido por PostGIS. |
| `ExportacionConDatosSimuladosIT.java` | `src/test/java/.../exportacion/` | **Datos simulados**: 30 días, 72 000 lecturas, 400 reservas, contra un cálculo independiente. |
| `LectorDeXlsx.java` | `src/test/java/.../exportacion/` | Utilidad compartida por las pruebas de integración. |
| `PasosDeExportacion.java` | `src/test/java/.../aceptacion/` | Pasos de Cucumber. |
| `exportar_los_datos_del_servicio.feature` | `src/test/resources/features/` | Los criterios de aceptación en Gherkin (`@HU-86`). |
| `CorsIT.java` | `src/test/java/.../seguridad/` | **Ajustada**: `Content-Disposition` ahora es cabecera expuesta. |

Si editas una prueba, edita el **original** y vuelve a copiarlo aquí.

## En tres comandos

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-22"          # cualquier JDK 21+
mvn test                                                   # 1) toda la suite (requiere Docker)
powershell -ExecutionPolicy Bypass -File test/HU-Desarrollo-86-exportar-datos/generar-resultados.ps1   # 2) RESULTADOS.txt
# 3) manuales: ver MANUAL_DE_PRUEBAS.md, sección 3
```
