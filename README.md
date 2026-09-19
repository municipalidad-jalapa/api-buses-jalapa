# EcoRuta

Plataforma del bus eléctrico municipal de Jalapa, Guatemala. Proyecto de Seminario de la
Universidad Mariano Gálvez, con la Municipalidad de Jalapa como sponsor.

**El problema que resuelve:** el bus solo sale cuando se reúnen 10 pasajeros, y hoy nadie puede ver
ese conteo. La funcionalidad central es el contador *"faltan N para que salga el bus"*. Todo lo
demás —mapa, ETA, notificaciones— orbita alrededor de eso.

Este documento busca dejarte trabajando en **menos de una hora**. Si te toma más, es un defecto del
documento: avisá para corregirlo.

---

## 1. Dónde está el código

El proyecto vive en **tres repositorios**. Esto es lo primero que confunde a quien llega:

| Repositorio | Qué tiene | Dónde |
|---|---|---|
| **Monorepo** | Documentación, ADR, colección de Postman, `docker-compose.yml` | GitHub: `WalyhU/ecoruta` |
| **Backend** | API Java + Spring Boot | GitLab: `municipalidad-jalapa/backend/api-buses-jalapa` |
| **App web** | Frontend React | GitLab: `municipalidad-jalapa/frontend/app-movil-buses` |

Para trabajar necesitás clonar el que te toque según tu rol. Para levantar el sistema completo,
los tres.

```bash
mkdir ecoruta && cd ecoruta
git clone https://github.com/WalyhU/ecoruta.git .
git clone https://gitlab.com/municipalidad-jalapa/backend/api-buses-jalapa.git
git clone https://gitlab.com/municipalidad-jalapa/frontend/app-movil-buses.git
```

Si el clon de GitLab te da **401**, pedí que te agreguen como miembro del proyecto: no es un
problema de tu configuración.

---

## 2. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| **Docker Desktop** | Cualquiera reciente | Base de datos, backend y pruebas de integración |
| **JDK** | 21 (LTS) | Compilar el backend |
| **Maven** | 3.9+ | Build del backend |
| **Node.js** | 20.19+ o 22.12+ | App web (Vite 7 no arranca con menos) |
| **Git** | Cualquiera reciente | — |

Verificá todo de una vez:

```bash
docker --version && java -version && mvn -version && node --version
```

**Docker tiene que estar corriendo**, no solo instalado. Es el requisito que más se olvida: sin él
no levanta la base ni corren las pruebas de integración.

---

## 3. Arranque

### Backend y base de datos

Desde el monorepo, con `docker-compose.yml`:

```bash
docker compose up --build
```

Levanta PostGIS y el backend en `http://localhost:8080`. Flyway aplica las migraciones al arrancar.

Sin Docker (requiere PostGIS local):

```bash
mvn spring-boot:run
```

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Colección de Postman: `docs/postman/EcoRuta.postman_collection.json` (importar en Postman;
  la variable `baseUrl` apunta a `http://localhost:8080`).

## Endpoints principales

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/api/v1/rutas` | público |
| `POST` | `/api/v1/demanda/registros` | público (UUID de dispositivo) |
| `GET` | `/api/v1/demanda/estado` | público |
| `POST` | `/api/v1/auth/login` | público — *pendiente, SCRUM-134 (Firebase)* |
| `POST` | `/api/v1/telemetria/posiciones` | **credencial de equipo** (`Authorization: Bearer eq_…`) |
| `GET` | `/api/v1/telemetria/posicion` | público |
| `GET` | `/api/v1/telemetria/stream` | público (SSE) |
| `POST` `GET` | `/api/v1/admin/vehiculos` | `ROLE_ADMIN` |
| `POST` | `/api/v1/admin/vehiculos/{id}/equipos` | `ROLE_ADMIN` — cambia el equipo del bus |
| `POST` `GET` | `/api/v1/admin/equipos` | `ROLE_ADMIN` |
| `POST` | `/api/v1/admin/equipos/{id}/revocacion` | `ROLE_ADMIN` |
| `POST` | `/api/v1/auth/admin` | público — idToken de Firebase → JWT de administrador (SCRUM-173) |
| `POST` | `/api/v1/admin/sesion/renovacion` | `ROLE_ADMIN` — renueva la sesión mientras hay actividad |
| `GET` | `/api/v1/admin/servicio` | `ROLE_ADMIN` — todas las rutas con su bus y última posición |
| `GET` | `/api/v1/admin/exportaciones/servicio?desde=&hasta=` | `ROLE_ADMIN` — descarga `.xlsx` con demanda y recorridos (Desarrollo-86) |
| `POST` | `/api/v1/integraciones/traccar/posiciones` | **token de integración** (`X-Traccar-Token`) — SCRUM-24 |

## Cuenta de administrador del panel municipal

El panel web municipal (SCRUM-173) usa el mismo mecanismo que el conductor: el navegador inicia
sesión en Firebase, envía el idToken a `POST /api/v1/auth/admin` y recibe un JWT propio del
backend con rol `admin`. **No hay contraseñas en el repositorio ni en la base**: la contraseña
vive solo en Firebase.

Crear la primera cuenta:

1. En la consola de Firebase del proyecto, *Authentication → Users → Add user*, con el correo
   institucional. Copiar el **User UID**.
2. Arrancar el backend con ese uid:

   ```bash
   ECORUTA_ADMIN_FIREBASE_UID=<uid> ECORUTA_ADMIN_USUARIO=jefe-transporte
   ```

   Al arrancar se crea la cuenta local con rol `ADMIN` si no existe (es idempotente; después
   la variable se puede quitar).

Cuentas adicionales, sin reiniciar:

```sql
INSERT INTO usuarios (username, rol, activo, firebase_uid)
VALUES ('otra-persona', 'ADMIN', TRUE, '<uid de Firebase>');
```

Para quitar el acceso: `UPDATE usuarios SET activo = FALSE WHERE firebase_uid = '<uid>';`.

- Una cuenta de conductor, una inexistente o una inactiva recibe **403** al intentar entrar.
- La sesión se cierra tras `PANEL_ADMIN_INACTIVIDAD_MINUTOS` (30 por defecto) sin actividad:
  el panel la renueva mientras se usa y, si no, el token vence y la API responde 401.
- El rol de administrador no está atado a ninguna ruta: ve el servicio completo.

## Exportar los datos del servicio (Desarrollo-86)

El administrador municipal descarga la demanda y los recorridos en una hoja de cálculo para sus
informes propios:

```bash
curl -H "Authorization: Bearer $JWT_ADMIN" -o servicio.xlsx \
     "localhost:8080/api/v1/admin/exportaciones/servicio?desde=2026-09-01&hasta=2026-09-15"
```

- **Rango de fechas:** `desde` y `hasta` (`AAAA-MM-DD`), inclusivos, en **días de Guatemala**
  (`ecoruta.exportacion.zona-horaria`), no en UTC. `hasta=2026-09-15` incluye hasta las 23:59 de
  ese día. Máximo `ecoruta.exportacion.rango-maximo-dias` (366) por descarga.
- **Tres hojas:** `Resumen` (periodo, totales y cómo leer el archivo), `Demanda` (una fila por día,
  ruta y parada: reservas creadas, abordaron, canceladas, expiradas, vigentes) y `Recorridos` (una
  fila por día y bus, desde el GPS: lecturas, primera y última, distancia estimada, velocidad
  promedio en movimiento, paradas atendidas). Fechas y números son de Excel, no texto.
- **No expone datos de un pasajero.** Las consultas de `ExportacionRepository` son solo
  agregaciones (`GROUP BY`): `dispositivo_id`, id de reserva, token de notificaciones y usuario del
  conductor nunca se seleccionan, así que no llegan ni a la memoria del proceso. La prueba
  `ExportacionServicioIT` busca el UUID de un dispositivo en **todas** las partes del `.xlsx`.
- **La distancia es una estimación:** suma los tramos entre lecturas consecutivas y omite los
  huecos de señal (más de `salto-maximo-segundos`, 300 por defecto); no es un odómetro.
- **Errores:** `400` falta una fecha o formato inválido, `401` sin sesión, `403` no es
  administrador, `422` rango invertido o mayor al máximo. Todos con el `ApiError` de siempre.
- El navegador puede leer el nombre sugerido del archivo (`Content-Disposition`) porque CORS lo
  expone. Como pide el JWT en `Authorization`, el panel lo descarga con `fetch` y un `Blob`, no con
  un `<a href>`.

## Integración con Traccar (GPS real)

El GPS del bus (103A/B) reporta a un servidor **Traccar**, y Traccar reenvía cada posición a
`POST /api/v1/integraciones/traccar/posiciones` (SCRUM-24). El backend la registra con el
mismo servicio de telemetría que usa el equipo a bordo: alimenta la posición vigente, el stream
en tiempo real y los avisos. El endpoint `POST /api/v1/telemetria/posiciones` no cambia.

**Las dos credenciales no se intercambian.** `Authorization: Bearer eq_…` autentica al equipo a
bordo en la telemetría; `X-Traccar-Token` autentica al servicio Traccar en esta integración.
Cada una solo abre su propia ruta.

1. Definir el secreto en el backend (mínimo 32 caracteres; sin él la integración responde 401 a
   todo):

   ```bash
   TRACCAR_TOKEN=<secreto-largo>
   # Unidad de position.speed: NUDOS (la de Traccar, por defecto), KMH o MS
   TRACCAR_UNIDAD_VELOCIDAD=NUDOS
   ```

2. Configurar el reenvío en `traccar.xml`:

   ```xml
   <entry key='forward.enable'>true</entry>
   <entry key='forward.url'>https://<api>/api/v1/integraciones/traccar/posiciones</entry>
   <entry key='forward.json'>true</entry>
   <entry key='forward.header'>X-Traccar-Token: <secreto-largo></entry>
   ```

3. Asociar el dispositivo (su *uniqueId* en Traccar, normalmente el IMEI) con el equipo del bus:

   ```sql
   INSERT INTO dispositivos_externos (identificador, equipo_id)
   VALUES ('860000000000001', <id del equipo ACTIVO del bus>);
   ```

Respuestas: `202 {"recibidas", "aceptadas", "descartadas"}` — un 202 no implica que todas se
guardaron: se descartan las lecturas fuera de la ventana de 12 h y los reenvíos repetidos (se
deduplican por `position.id`). `400` cuerpo mal formado, `401` token ausente o inválido, `422`
dispositivo sin equipo asociado (o con el equipo revocado o sin vehículo), o datos inválidos; en
ese caso no se registra nada del reenvío.

## Provisionar un equipo a bordo

El equipo que reporta el GPS se autentica con **credencial propia**, no con la sesión de un
conductor (SCRUM-142). Así el módulo no depende de que nadie tenga sesión abierta, y se puede
revocar un solo equipo sin afectar a los demás.

```bash
curl -X POST localhost:8080/api/v1/admin/equipos \
     -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"vehiculoId": 1, "etiqueta": "Tableta cabina BUS-01"}'
```

```json
{"id":1,"codigoPublico":"eq_Uiay5Tc2kAB9",
 "credencial":"eq_Uiay5Tc2kAB9.gS1beUuHqfCxaNTVaCDDV2USfqkrU38y-RgtBI8pQZM",
 "etiqueta":"Tableta cabina BUS-01","vehiculo":"BUS-01"}
```

**La credencial se muestra una sola vez.** En la base solo queda su hash bcrypt: no se puede
recuperar. Si se pierde, se revoca el equipo y se emite otro.

El equipo la manda en cada lote:

```bash
curl -X POST localhost:8080/api/v1/telemetria/posiciones \
     -H "Authorization: Bearer eq_Uiay5Tc2kAB9.gS1beUuH..." \
     -H 'Content-Type: application/json' \
     -d '{"posiciones":[{"latitud":14.6335,"longitud":-89.9885,"velocidadKmh":18,
                         "timestamp":"2026-08-17T10:00:00Z"}]}'
```

Nunca en la URL: el secreto solo se acepta en la cabecera `Authorization`, porque los access
logs de un proxy inverso registran la URL pero no las cabeceras.

Revocar corta el acceso en la petición siguiente, sin esperar ninguna expiración:

```bash
curl -X POST localhost:8080/api/v1/admin/equipos/1/revocacion -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN"
```

## Cambiar el equipo de un bus

Cambiar el aparato físico es un trámite de datos, no una migración (SCRUM-143). Revoca el
equipo activo y emite otro en una sola transacción, **sin perder el histórico del vehículo**:
las posiciones ya escritas siguen atribuidas a ese bus.

```bash
curl -X POST localhost:8080/api/v1/admin/vehiculos/1/equipos \
     -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"etiqueta": "Tableta de repuesto"}'
```

Un bus lleva un solo equipo activo a la vez, garantizado por un índice único parcial en la base.
Consultar la posición de un vehículo concreto: `GET /api/v1/telemetria/posicion?vehiculoId=1`.

> La credencial solo es confidencial sobre TLS. En producción el proxy inverso termina HTTPS
> (ADR-006); en desarrollo local viaja en claro.

## Simular el bus en movimiento

El bus real todavia no lleva el equipo instalado. Sin posiciones entrando, la pantalla del
pasajero sale con el marcador quieto: no se puede desarrollar el mapa ni ensenarlo. El simulador
recorre la ruta y reporta posiciones **por el mismo camino que usaria el equipo real** —misma
credencial de dispositivo, mismo endpoint— asi que lo que se ve en la demo es la ruta de datos
de produccion, no un atajo.

No inventa el recorrido: pide `GET /api/v1/rutas` y camina el `trazado`, que son los vertices
reales de las calles. Si la ruta cambia en la base, el simulador cambia con ella.

### Arrancar

```bash
export ECORUTA_ADMIN_TOKEN='solo-para-desarrollo-local-no-usar-en-produccion'
python3 tools/simulador-gps.py
```

Solo necesita Python 3, nada que instalar. En el primer arranque aprovisiona un equipo y imprime
su credencial; guardala y reusala con `--credencial` para no crear uno nuevo cada vez:

```bash
python3 tools/simulador-gps.py --credencial eq_AokFXQjVmNSW.8y8tT23HaA4...
```

### Parar

`Ctrl-C` en su terminal. Si lo lanzaste en segundo plano:

```bash
pkill -f simulador-gps.py
```

Parar el simulador **no borra nada**: la ultima posicion se queda como vigente y el mapa la sigue
mostrando con su hora. Es el mismo comportamiento que con el bus apagado.

### Ajustar el recorrido

| Opcion | Por defecto | Para que |
|---|---|---|
| `--velocidad` | `30` | km/h del bus. 30 es realista en ciudad; `--velocidad 120` para una vuelta rapida en una demo |
| `--intervalo` | `2` | segundos entre reportes. Bajarlo da un movimiento mas fluido y mas carga |
| `--espera-parada` | `5` | segundos detenido en cada parada. En parada reporta 0 km/h |
| `--vueltas` | `0` | numero de vueltas; `0` es sin fin |
| `--api` | `http://localhost:8080` | contra que backend reportar |
| `--credencial` | — | equipo ya aprovisionado; si falta, crea uno |

La vuelta completa son 5.17 km, asi que a 30 km/h dura unos 10 minutos y a 120 unos 2.5.

```bash
# Una sola vuelta, rapida, para comprobar que la cadena entera funciona
python3 tools/simulador-gps.py --velocidad 300 --intervalo 0.5 --vueltas 1
```

### La ruta que recorre

> **No es la ruta real del bus.** `V6__circuito_de_ejemplo.sql` siembra un circuito **inventado
> para la demo**, con nombres de parada inventados tambien. La ruta oficial la fija HU-41
> (SCRUM-136), que esta en Sprint 5 y necesita levantamiento en campo. Por eso la ruta se llama
> *"Ruta de ejemplo - Centro de Jalapa"*: para que nadie la tome por buena.

Lo unico real son las **calles**. El circuito va del **Parque Central** por la **6a Avenida** y la
**1a Calle** hacia el oeste, gira en **Llano Grande** y vuelve por la **Calle Transito Rojas** hasta
cerrar en el parque. Sus 74 vertices salen de OpenStreetMap enrutados sobre la red vial, que es
por lo que la linea cae encima de las calles en vez de atravesar manzanas.

La migracion se aplica en **todos los entornos**, tambien QA y produccion: `flyway.enabled` es
`true` y el proyecto no tiene perfiles de Spring. Es deliberado — lo que hay hoy en produccion
tambien es semilla inventada (V2), y esta al menos cae sobre calles de verdad.

## Flujo de una coordenada, de punta a punta

Util cuando algo no se ve en el mapa: dice en que eslabon mirar.

```
 simulador (o equipo a bordo)
   |  POST /api/v1/telemetria/posiciones   Authorization: Bearer eq_<codigo>.<secreto>
   v
 EquipoAuthFilter          flota/seguridad/EquipoAuthFilter.java
   |  valida la credencial contra el hash bcrypt y rechaza las revocadas
   v
 TelemetriaController      telemetria/web/TelemetriaController.java:62
   |
   v
 TelemetriaService         telemetria/servicio/TelemetriaService.java:70
   |  guarda cada PosicionHistorica y publica PosicionVigenteActualizada (:89)
   v
 DifusorDePosiciones       telemetria/web/DifusorDePosiciones.java
   |  @TransactionalEventListener(AFTER_COMMIT): difunde solo lo ya confirmado
   |  GET /api/v1/telemetria/stream  (SSE, publico)
   v
 flujoDePosiciones.ts      frontend: src/core/flujoDePosiciones.ts
   |  EventSource escuchando el evento 'posicion'
   v
 usePosicionBus.ts         frontend: src/hooks/usePosicionBus.ts
   |  combina la carga inicial (GET /telemetria/posicion) con el flujo en vivo
   v
 MapaOpenStreetMap.tsx     frontend: src/componentes/MapaOpenStreetMap.tsx
      mueve el marcador del bus; la linea la dibuja del `trazado` de la ruta
```

Dos detalles que explican casi todos los sustos:

- **El evento se difunde despues del commit**, no dentro. Un `@EventListener` normal empujaria al
  pasajero una posicion que todavia puede hacer rollback.
- **El frontend escucha `addEventListener('posicion', ...)`, no `onmessage`.** El backend nombra
  el evento, y `onmessage` solo recibe los que van sin nombre: con `onmessage` no llega nada y no
  hay ningun error que lo delate.

Para verlo crudo, sin frontend:

```bash
curl -N localhost:8080/api/v1/telemetria/stream
```

## Ver el bus moverse en tiempo real

El stream empuja cada posición apenas se ingesta, sin que el cliente pregunte (ADR-008). Al
conectarse recibe de inmediato la posición vigente, para que el mapa no arranque en blanco.

En una terminal, quedarse escuchando:

```bash
curl -N localhost:8080/api/v1/telemetria/stream
```

En otra, ingestar una posición (ver "Provisionar un equipo a bordo" para obtener `$CRED`). En la
primera terminal aparece al instante:

```
id:1
event:posicion
retry:3000
data:{"latitud":14.6335,"longitud":-89.9885,"velocidadKmh":18.0,...,"vehiculo":"BUS-01"}
```

Dejándola abierta sin ingestar nada, cada 25 s llega un `:latido`. Es un comentario SSE que el
navegador ignora; existe porque Nginx cierra las conexiones inactivas a los 60 s y con el bus
parado en la terminal no habría nada que difundir.

Desde el navegador son tres líneas, sin librerías:

```js
const stream = new EventSource('/api/v1/telemetria/stream');
stream.addEventListener('posicion', e => moverMarcador(JSON.parse(e.data)));
```

`EventSource` reconecta solo si se corta la conexión, que es la razón por la que ADR-008 eligió
SSE sobre WebSocket.

## Configuración

Por variables de entorno: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
`ECORUTA_ADMIN_TOKEN`. Los del compose son solo de desarrollo.

`ECORUTA_ADMIN_TOKEN` es **provisional** (SCRUM-142, se retira con SCRUM-134): concede
`ROLE_ADMIN` por la cabecera `X-Admin-Token` mientras no exista la autenticación de personas
con Firebase. **Si la variable no está definida, nadie es admin y `/api/v1/admin/**` rechaza a
todos** — falla cerrado, que es lo que corresponde en producción.

Reglas de negocio configurables en `application.yml`: `ecoruta.demanda.umbral-salida` (10),
`ttl-minutos` (20), `geocerca-metros` (150), `ecoruta.telemetria.ventana-horas` (12).

## Pruebas

```bash
mvn test
```

Integración con Testcontainers e imagen `postgis/postgis:17-3.5` (requiere Docker en ejecución).
H2 no sirve: no tiene PostGIS y buena parte del dominio son columnas `geometry` (ADR-007).

Las pruebas `*IT` corren con surefire, no con failsafe, porque el pipeline ejecuta `mvn test`
y no `mvn verify`. Están declaradas en los `includes` del plugin: si se quitan de ahí se
compilan pero no se ejecutan, y el build sale verde igual.

### Pruebas de aceptación en Gherkin

Los criterios de aceptación de Jira están escritos como escenarios ejecutables en
`src/test/resources/features/*.feature`, en español, con Cucumber. Cada escenario lleva la
etiqueta de su historia (`@SCRUM-142`) y de su criterio (`@criterio-c`), así que la matriz de
trazabilidad de HU-33 sale del propio código en vez de mantenerse a mano.

```bash
mvn test -Dtest=PruebasDeAceptacionTest              # solo los escenarios
mvn test -Dcucumber.filter.tags="@SCRUM-143"         # solo los de una historia
mvn test -Dcucumber.filter.tags="@criterio-c"        # solo los de un criterio
```

Deja dos reportes en `target/cucumber/`: `cucumber.xml`, que GitLab publica en el Merge Request
con el nombre de cada escenario en español, y `reporte.html` para abrir en el navegador.

Convenciones al escribir escenarios:

- **Gherkin es para los criterios de aceptación, no para todo.** Si un escenario no se le puede
  leer en voz alta al product owner, va como prueba JUnit. Lo técnico —el orden `(lat, lon)` de
  PostGIS, el parseo del token— vive en `*Test`/`*IT`.
- **Cuidado con los números.** Con `# language: es`, Cucumber interpreta `{double}` en locale
  español, donde el punto es separador de **miles**: `14.6335` se convierte en `146335`. Por eso
  los escenarios no llevan coordenadas.
- El runner **debe** llamarse `*Test` o `*IT`, por los `includes` de surefire.
- `@CucumberContextConfiguration` va en una sola clase, y hereda de `IntegracionPostgisTest`
  para compartir el contenedor: si declarara sus propias anotaciones, Spring levantaría un
  segundo contexto con un segundo PostGIS.
