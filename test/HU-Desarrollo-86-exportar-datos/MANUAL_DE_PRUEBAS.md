# Manual de pruebas — HU Desarrollo-86 (exportar los datos del servicio)

Cómo ejecutar **todas** las pruebas de esta HU: las automáticas (unitarias e integración,
con datos simulados y con datos reales) y las manuales end-to-end.

> Los archivos de prueba están copiados en [`casos/`](casos/) para lectura. **Los que Maven
> ejecuta de verdad viven en `src/test/`** (Maven exige esa ruta); no borres los originales.

Criterios de aceptación (Jira, Desarrollo-86):

1. Exportación en formato de hoja de cálculo desde el panel web.
2. Rango de fechas seleccionable.
3. La exportación no expone datos que identifiquen a un pasajero.

---

## 1. Requisitos del entorno

| Requisito | Detalle |
|---|---|
| **JDK 21+** | El `pom.xml` fija `java.version = 21`. Compila con cualquier JDK 21 o más nuevo (no con un JRE, hace falta `javac`). |
| **Maven** | 3.9+. |
| **Docker** | Las pruebas `*IT` levantan `postgis/postgis:17-3.5` con Testcontainers. Docker Desktop debe estar **corriendo** (`docker info` no debe fallar). |
| **Python 3** | Solo para las pruebas manuales (`tools/simulador-gps.py` y `leer_xlsx.py`). Sin librerías: solo la estándar. |
| **curl, bash** | Para el script manual (Git Bash en Windows los trae). |

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-22"     # Git Bash; cualquier JDK 21+
docker info                                          # si falla: abrir Docker Desktop y esperar ~30 s
```

PowerShell: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"`.

---

## 2. Pruebas automáticas

Todas con **`mvn test`** desde la raíz del proyecto (`api-buses-jalapa/`).

### 2.1 Solo esta HU

```bash
mvn test -Dtest='LibroDelServicioTest,ExportacionServiceTest,ExportacionServicioIT,ExportacionConRutaRealIT,ExportacionConDatosSimuladosIT,CorsIT' \
         -Dsurefire.failIfNoSpecifiedTests=false
mvn test -Dtest=PruebasDeAceptacionTest -Dcucumber.filter.tags="@HU-86"     # escenarios Gherkin
```

### 2.2 Suite completa (todo el backend)

```bash
mvn test
```
Al final debe decir `BUILD SUCCESS`. Para dejar el estado de **todas** las pruebas en un
`.txt`, ver la sección 5.

### 2.3 Qué prueba cada clase

| Clase | Tipo | Datos | Docker | Qué prueba |
|---|---|---|---|---|
| `LibroDelServicioTest` (8) | Unitaria | Filas armadas a mano | No | El `.xlsx` leído de vuelta con POI: 3 hojas en orden, fechas y números **reales de Excel** (no texto), metros → km, horas en zona de Guatemala, bus sin ruta/sin movimiento, archivo vacío, un nombre que parece fórmula (`=HYPERLINK…`) queda como texto, ningún encabezado nombra a un pasajero. |
| `ExportacionServiceTest` (8) | Unitaria | Mocks | No | El rango: inclusivo, de medianoche de Guatemala a medianoche del día siguiente (UTC-6), misma ventana para demanda y recorridos, 1 solo día válido, invertido → 422 **sin consultar la base**, tope de 366 días inclusivo, valores por defecto, zona horaria inválida falla al arrancar. |
| `ExportacionServicioIT` (13) | Integración | Sembrados a mano por SQL | **Sí** | El endpoint completo contra PostGIS: descarga con `Content-Type`/`Content-Disposition`/`no-store`, demanda agrupada por día y parada, distancia y huecos de señal, **extremos del rango a las 23:59/00:00 de Guatemala**, 400/401/403/422 con `ApiError`, y la **privacidad**: busca UUID y token de notificaciones en *todas* las partes del ZIP. |
| `ExportacionConRutaRealIT` (3) | Integración | **Reales**: calles de las rutas sembradas (V6/V12, OpenStreetMap) | **Sí** | Cada vértice del trazado real es una lectura GPS; la distancia exportada debe igualar el largo de la línea medido aparte por PostGIS (`ST_Length`), ±0.5 %. Las 8 paradas reales con su número y nombre. Paradas atendidas por el conductor (y que el usuario del conductor no viaja en el archivo). |
| `ExportacionConDatosSimuladosIT` (1) | Integración | **Simulados**, 30 días, 72 000 lecturas, 400 reservas (semilla fija) | **Sí** | Fila por fila contra un **cálculo independiente en Java**; incluye reservas que caen en el borde del rango por la zona horaria, un hueco de señal diario de 2 h que **no** debe sumar, velocidad promedio ignorando 0 km/h, los 400 UUID ausentes del archivo y el tiempo de exportar (~2 s). |
| `CorsIT` (6) | Integración | — | **Sí** | Ajustado: `Content-Disposition` ahora es cabecera expuesta, para que el panel lea el nombre del archivo. |
| `exportar_los_datos_del_servicio.feature` (5 escenarios) | Aceptación (Gherkin) | Sembrados | **Sí** | Los tres criterios en lenguaje de negocio + acceso sin sesión (`@HU-86`, `@criterio-1/2/3`). |

Informes de detalle: `target/surefire-reports/*.txt` y `target/cucumber/reporte.html`.

---

## 3. Pruebas manuales end-to-end

Se hacen contra la **aplicación real por HTTP**, como lo haría el panel. El script
[`prueba-manual.sh`](prueba-manual.sh) tiene dos modos:

| Modo | Qué hace | Cuándo |
|---|---|---|
| `MODO=simulado` (por defecto) | **Genera** los datos por los mismos caminos que producción —pasajeros con `POST /api/v1/reservas`, el bus con `tools/simulador-gps.py`— y comprueba que el archivo trae *exactamente* lo generado (15 reservas, 3 canceladas, 2 abordaron, 1 vuelta de ~5.17 km…). | Local, base vacía. |
| `MODO=real` | **No genera nada**: exporta lo que ya hay en el entorno y comprueba lo verificable sin conocer los datos: formato, errores, privacidad (ningún UUID en el archivo) y, con acceso a la base, que los totales coincidan con un SQL independiente. | QA o base con uso real. |

### 3.1 Preparar (local, aislado)

```bash
# 1) Base PostGIS desechable. NO uses "docker compose up" si ya tienes datos en su volumen.
docker run -d --name ecoruta-hu86-db -e POSTGRES_DB=ecoruta -e POSTGRES_USER=ecoruta \
       -e POSTGRES_PASSWORD=ecoruta -p 55432:5432 postgis/postgis:17-3.5

# 2) La aplicación (Flyway crea el esquema y siembra las 2 rutas y sus paradas)
export JAVA_HOME="/c/Program Files/Java/jdk-22"
export DB_PORT=55432 ECORUTA_ADMIN_TOKEN="solo-para-desarrollo-local-no-usar-en-produccion" \
       CORS_ORIGENES="https://qa.buses.jalapa.gob.gt"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8086"
```
Espera a `Started EcoRutaApplication` (`curl localhost:8086/actuator/health` → `UP`).

### 3.2 Modo simulado

En otra terminal:

```bash
BASE_URL=http://localhost:8086 \
ADMIN_TOKEN="solo-para-desarrollo-local-no-usar-en-produccion" \
CORS_ORIGEN="https://qa.buses.jalapa.gob.gt" \
DB_CONTENEDOR=ecoruta-hu86-db \
bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh
```

Tarda ~1 minuto (la vuelta del simulador). Al final: `TODO OK` y `0 fallo(s)`. Imprime también las
hojas `Resumen`, `Demanda` y `Recorridos` para que las **leas tú**, no solo las máquinas.

Si `python` no es el intérprete correcto: `PYTHON=py` o `PYTHON=python3`.

### 3.3 Modo real (datos reales del entorno)

```bash
MODO=real \
BASE_URL=https://<api-de-qa> \
ADMIN_JWT="<JWT del administrador: inicia sesión en el panel y cópialo de la petición a /api/v1/admin/servicio>" \
DB_CONTENEDOR=<opcional: contenedor con psql si tienes acceso a la base> \
bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh
```

Además de lo automático, con datos reales haz esta **revisión humana** (5 minutos):

| # | Qué hacer | Qué debe pasar |
|---|---|---|
| 1 | Abre el `.xlsx` en Excel/LibreOffice. | Abre sin advertencias de "archivo dañado"; 3 hojas; encabezados fijos y filtro en cada tabla. |
| 2 | Ordena y filtra `Demanda` por ruta; suma la columna *Reservas creadas*. | Fechas y números se comportan como tales (no como texto); la suma coincide con el `Resumen`. |
| 3 | Contrasta el día de más demanda con la base (SQL de abajo). | Las cifras coinciden. |
| 4 | Contrasta los km de un bus con lo que sabes del servicio ese día. | Plausible (es una **estimación**, no un odómetro). |
| 5 | Busca (Ctrl+F, "todo el libro") un identificador de dispositivo real que conozcas. | No aparece en ninguna hoja. |
| 6 | Elige un rango que cruce la medianoche de un día con datos (por ejemplo del 15 al 15). | Solo trae lo de ese día de Guatemala. |

SQL de contraste (día de Guatemala, no UTC):

```sql
-- Demanda del día, por parada
SELECT (r.creado_en AT TIME ZONE 'America/Guatemala')::date AS fecha, p.orden, p.nombre,
       count(*) AS reservas,
       count(*) FILTER (WHERE r.estado = 'ABORDO')    AS abordaron,
       count(*) FILTER (WHERE r.estado = 'CANCELADA') AS canceladas,
       count(*) FILTER (WHERE r.estado = 'EXPIRADA')  AS expiradas
  FROM registros_espera r JOIN paradas p ON p.id = r.parada_id
 WHERE (r.creado_en AT TIME ZONE 'America/Guatemala')::date = DATE '2026-09-15'
 GROUP BY 1, 2, 3 ORDER BY 2;

-- Lecturas GPS del día, por bus
SELECT v.identificador, count(*) AS lecturas, min(p.registrado_en), max(p.registrado_en)
  FROM posiciones_historicas p JOIN vehiculos v ON v.id = p.vehiculo_id
 WHERE (p.registrado_en AT TIME ZONE 'America/Guatemala')::date = DATE '2026-09-15'
 GROUP BY 1;
```

> **Sobre "datos reales".** El circuito sembrado (V6/V12) usa calles verdaderas de Jalapa pero **no es
> la ruta oficial** (lo dice V6; la oficial la fija HU-41), y el bus real aún no lleva el equipo
> instalado. Por eso "real" en el modo automático significa *geometría real de calles*, y en el
> modo manual `real` significa *lo que haya en el entorno* que se apunte. Con el servicio en
> operación, el modo `real` y la revisión humana son la prueba definitiva.

### 3.4 Probar a mano con Swagger o Postman

- Swagger: `http://localhost:8086/swagger-ui.html` → *Panel municipal* → `GET /api/v1/admin/exportaciones/servicio`
  (botón *Authorize* no aplica: usa la cabecera `X-Admin-Token`).
- Postman: `docs/postman/EcoRuta.postman_collection.json` → *Extras* → **Exportar datos del servicio (.xlsx)**
  → *Send and Download*.
- `curl`:
  ```bash
  curl -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN" -o servicio.xlsx \
       "http://localhost:8086/api/v1/admin/exportaciones/servicio?desde=2026-09-01&hasta=2026-09-15"
  python test/HU-Desarrollo-86-exportar-datos/leer_xlsx.py servicio.xlsx --resumen
  ```

### 3.5 Limpiar

`Ctrl+C` en la app y `docker rm -f ecoruta-hu86-db`. El script deja datos de prueba en la base
(reservas, lecturas): por eso conviene la base desechable.

---

## 4. Trazabilidad: criterio de aceptación → prueba

| # | Criterio (Desarrollo-86) | Prueba automática | Prueba manual |
|---|---|---|---|
| 1 | Hoja de cálculo | `ExportacionServicioIT.devuelve_un_xlsx_como_descarga…`, `LibroDelServicioTest.trae_tres_hojas…` y `…numeros_y_fechas_reales`, escenario *"La exportación es una hoja de cálculo"* | `prueba-manual.sh` §5 (`Content-Type`, ZIP `PK`, hojas) + revisión humana 1-2 |
| 1 | Descarga desde el panel (cabeceras) | `ExportacionServicioIT` (`Content-Disposition`, `no-store`), `CorsIT.stream_expone_las_cabeceras…` | `prueba-manual.sh` §5 y §9 (CORS) |
| 2 | Rango seleccionable | `ExportacionServicioIT.solo_entra_lo_que_cae_dentro_del_rango…`, `…cambiar_el_rango…`, `ExportacionServiceTest.el_rango_es_inclusivo…`, `ExportacionConDatosSimuladosIT` | `prueba-manual.sh` §4 y §7 + revisión humana 6 |
| 2 | Rango inválido rechazado | `ExportacionServicioIT` (422, 400), `ExportacionServiceTest.un_rango_invertido…` | `prueba-manual.sh` §4 |
| 3 | Sin datos de pasajeros | `ExportacionServicioIT.el_archivo_no_contiene_ningun_identificador…`, `…cada_fila_es_un_total…`, `ExportacionConDatosSimuladosIT` (400 UUID), `LibroDelServicioTest.ninguna_columna…` | `prueba-manual.sh` §8 + revisión humana 5 |
| — | Solo el administrador | `ExportacionServicioIT.sin_sesion_responde_401`, `…un_conductor_no_puede_exportar` | `prueba-manual.sh` §3 |
| — | Exactitud de los números | `ExportacionConRutaRealIT`, `ExportacionConDatosSimuladosIT` | `prueba-manual.sh` §6 y §10 + revisión humana 3-4 |

---

## 5. Estado de todas las pruebas (`RESULTADOS.txt`)

Después de correr `mvn test`, genera el reporte (PowerShell, desde la raíz):

```powershell
powershell -ExecutionPolicy Bypass -File test/HU-Desarrollo-86-exportar-datos/generar-resultados.ps1
```

Lee `target/surefire-reports/TEST-*.xml` y escribe `RESULTADOS.txt` con: el veredicto global
(**TODAS PASARON** / **HAY FALLOS**), el conteo, una tabla por clase y el detalle de cada prueba
(`PASS` / `FAIL`).

Para anexar también la salida de las pruebas manuales, guarda cada corrida como `manual-*.log` en una
carpeta y pásala con `-SalidasManuales`:

```bash
mkdir -p /tmp/salidas
bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh 2>&1 | tee /tmp/salidas/manual-1-simulado.log
MODO=real bash test/HU-Desarrollo-86-exportar-datos/prueba-manual.sh 2>&1 | tee /tmp/salidas/manual-2-real.log
```
```powershell
powershell -ExecutionPolicy Bypass -File test/HU-Desarrollo-86-exportar-datos/generar-resultados.ps1 -SalidasManuales C:\ruta\a\salidas
```

Ojo: el script usa `mvn -v` para informar el JDK; si `JAVA_HOME` apunta a otro JDK que el que usaste
para `mvn test`, el encabezado del reporte lo mostrará distinto.

---

## 6. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `invalid target release: 21` / `No compiler is provided` | `mvn` usa un JDK menor a 21 o un JRE | Apuntar `JAVA_HOME` a un **JDK** 21+ |
| `Could not find a valid Docker environment` | Docker Desktop apagado | Abrirlo y esperar a que `docker info` responda |
| `No tests were executed` con `-Dtest=…` | Un nombre de clase mal escrito | Añadir `-Dsurefire.failIfNoSpecifiedTests=false` y revisar el nombre |
| El script: `Falta ADMIN_TOKEN` | No se pasó el token | `ADMIN_TOKEN` = el `ECORUTA_ADMIN_TOKEN` con el que arrancó la app |
| El simulador: `Falta ECORUTA_ADMIN_TOKEN` | El modo simulado necesita `ADMIN_TOKEN` (no sirve solo `ADMIN_JWT`) para aprovisionar el equipo | Pasar `ADMIN_TOKEN` |
| `[FALLO] … reservas creadas` con 422 | Ya había reservas del mismo dispositivo/ritmo, o la base no está vacía | Usar la base desechable (§3.1) |
| Las cifras de "hoy" no coinciden con el SQL | El SQL usa UTC | Convertir con `AT TIME ZONE 'America/Guatemala'` |
| Puerto 8086 en uso | Otra instancia | Cambiar `-Dserver.port` y `BASE_URL` |
| El navegador no ve el nombre del archivo | Falta `CORS_ORIGENES` con el origen del panel (SCRUM-274) | Definir `CORS_ORIGENES` en el despliegue |
