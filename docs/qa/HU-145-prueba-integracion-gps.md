# HU-145: Prueba de integracion GPS 103A/B → Traccar → EcoRuta

**Jira:** [SCRUM-25](https://municipalidad.atlassian.net/browse/SCRUM-25) (criterio 4: cambios de produccion permitidos si quedan documentados).
**Rama:** `SCRUM-25-hu-desarrollo-145-prueba-integracion-gps`
**Estado:** Ejecutada con emulador. El criterio 1 queda **pendiente de hardware real**.

## Historia

Como municipalidad quiero comprobar que una posicion real del GPS aparece en la aplicacion
atribuida al bus correcto, para saber que la integracion sirve y no solo que compila.

## Entorno

| Campo | Valor |
|-------|--------|
| Commit de `develop` | `d3a4571` Parametrizar geocerca-metros por variable de entorno (ADR-002) |
| Fecha | 2026-09-20 |
| Origen | Emulador (`SimuladorGps --modo traccar`). **No** se uso un TK103A/B real. |
| Traccar | imagen oficial `traccar/traccar:6.15.3` (Testcontainers) |
| Protocolo / puerto | `tk103` / 5002 (alternativa `gps103` / 5001) |
| Registro de desconocidos | `database.registerUnknown=true` |
| Reenvio | `forward.type=json` + `forward.url` + `forward.header` + `forward.retry.enable=true` |
| Backend | Spring Boot RANDOM_PORT + PostGIS Testcontainers (`postgis/postgis:17-3.5`) |
| Simulador | `src/test/java/gt/muni/jalapa/ecoruta/herramientas/SimuladorGps.java` |

Ejecucion:

```bash
TRACCAR_E2E=true mvn test -Dtest=IntegracionGpsTraccarIT
```

Sin `TRACCAR_E2E=true` la clase se omite y no altera el resto de `mvn test`.

Totales de la suite (Docker Desktop encendido):

| Corrida | Tests run | Failures | Skipped |
|---------|-----------|----------|---------|
| Antes (develop, sin estas pruebas) | 387 | 0 | 0 |
| Hallazgo 3, ANTES del arreglo (`n_posiciones_distintas...`) | 1 | 1 | 0 |
| Hallazgo 3, DESPUES del arreglo (`n_posiciones_distintas...`) | 1 | 0 | 0 |
| `IntegracionGpsTraccarIT` con `TRACCAR_E2E=true` | 7 | 0 | 0 |
| `mvn test` (sin `TRACCAR_E2E`) | 409 | 0 | 7 |

## Criterios

| Criterio | Prueba | Salida real | Estado |
|----------|--------|-------------|--------|
| C1 cadena completa | `criterio_1_emulado_la_cadena_completa_registra_la_posicion_del_bus_a` (DisplayName **EMULADO**) | Fila en `posiciones_historicas` con `clave_origen` `traccar:<uniqueId>:<epoch>` y `vehiculo_id` de BUS-01. | Cumplido con emulador / Pendiente de hardware |
| C2 GET `/telemetria/posicion?vehiculoId=` | `criterio_2_la_posicion_es_del_vehiculo_correcto_y_no_del_otro` | vehiculoId=A → 200, `vehiculo=BUS-01`. vehiculoId=B → 204. | Cumplido con emulador |
| C3 GET `/telemetria/stream` | `criterio_3_el_stream_emite_el_evento_posicion_al_cliente_suscrito` | Suscripcion **antes** de emitir; llega `event:posicion` con BUS-01. | Cumplido con emulador |
| C4 app web | Manual (no ejecutado; ver comandos abajo) | Pendiente de corrida con `app-movil-buses` develop. | Pendiente (manual) |
| C5 reenvio repetido | `criterio_5_el_mismo_payload_no_duplica_fila_ni_evento` | Mismo JSON al adaptador: 2ª respuesta `aceptadas=0`, `descartadas=1`. | Cumplido con emulador |
| C5b mismo fix por TCP | mismo metodo, segunda mitad | Dos paquetes con el mismo `fixTime`: **una** fila con `traccar:<uniqueId>:<epoch>`. | Cumplido con emulador (tras el arreglo) |
| C6 ventana 12 h | `criterio_6_fuera_de_la_ventana_de_12h_queda_en_descartadas` | Traccar reenvio; adaptador descarta. Directo: `descartadas=1`. | Cumplido con emulador |
| C7 evidencia PMO | este documento | | Cumplido (emulador) |

## Hallazgos

### Config de forward (paso 0)

El README de la API documenta `forward.enable=true` y `forward.json=true`. En Traccar **6.15.3** esas claves **ya no existen**.

- El reenvio se activa solo con `forward.url`.
- `forward.type` por defecto es `url`: Traccar hace **GET** con placeholders. El adaptador es `POST` JSON: **no lo recibiria**.
- Lo que funciona: `forward.type=json`, `forward.url`, `forward.header=X-Traccar-Token: …`, `forward.retry.enable=true`.
- `database.registerUnknown=true` es necesario: si no, Traccar ignora el IMEI del emisor.

Diff propuesto del README: no aplicado (ver reporte de entrega).

### JSON real del reenvio vs ReenvioTraccar

Captura real (`forward.type=json`), 2026-09-20, listener temporal de `IntegracionGpsTraccarIT`. Fechas ISO-8601 (`+00:00`), no epoch. `speed` en nudos (~10 = 18.52 km/h).

```json
{
  "position": {
    "id": 0,
    "attributes": {"distance": 0.0, "totalDistance": 0.0, "motion": true},
    "deviceId": 1,
    "protocol": "tk103",
    "serverTime": "2026-09-20T23:37:57.312+00:00",
    "deviceTime": "2026-09-20T23:37:57.000+00:00",
    "fixTime": "2026-09-20T23:37:57.000+00:00",
    "valid": true,
    "latitude": 14.6335,
    "longitude": -89.9885,
    "altitude": 0.0,
    "speed": 10.000003640000001,
    "course": 0.0
  },
  "device": {
    "id": 1,
    "uniqueId": "860000000000001",
    "name": "860000000000001",
    "status": "offline",
    "positionId": 0
  }
}
```

`position.id` llega en **0** porque el reenvio ocurre **antes** de persistir la posicion. Issue abierto: [traccar/traccar#4529](https://github.com/traccar/traccar/issues/4529).

EcoRuta lee: `position.id`, `latitude`, `longitude`, `speed`, `fixTime`/`deviceTime`, `device.uniqueId`. El resto lo ignora (`@JsonIgnoreProperties`).

### Protocolo

Coban TK103A/B = `tk103` puerto 5002. Serie GPS103 = `gps103` puerto 5001. Muchos clones "TK103B" hablan `gt06` o `h02`. Mensajes copiados de `Tk103ProtocolDecoderTest` / `Gps103ProtocolDecoderTest` de Traccar 6.15.3. Traccar decodifico el tk103 del emisor (`protocol: tk103` en el JSON).

### Valid / 0,0 (solo reporte; no se arreglo)

El adaptador **no** mira `position.valid`. Una posicion con validez V y coordenadas 0,0 **se registro** (filas 0 → 1). Hallazgo, no criterio. No se toco codigo por esto.

### Duplicado por retransmision (C5b)

Dos paquetes TCP con el mismo `fixTime`. Tras el arreglo: filas 1 → 2 (una fila nueva con `traccar:860000000000001:1789947756000`). La segunda emision no duplico. La deduplicacion del criterio 5 (mismo JSON al adaptador) sigue funcionando.

### Simulador

Vive en `SimuladorGps` (Java 21, JDK puro). Misma geometria y opciones de recorrido, mas `--modo traccar`.

## Cambios de codigo necesarios

**Archivo:** `src/main/java/.../integraciones/traccar/servicio/LecturaTraccar.java`
**Por que:** Traccar 6.15.3 reenvia `position.id=0` (ver [traccar/traccar#4529](https://github.com/traccar/traccar/issues/4529)). El adaptador trataba `0` como id valido y armaba `clave_origen=traccar:0`. Todas las lecturas de una instancia nueva colisionaban en una sola fila.
**Autorizacion:** SCRUM-25, criterio 4 (cambio de produccion documentado). Unica excepcion a "no tocar `src/main`".

**Antes:**

```java
String clave = posicion.id() != null
        ? "traccar:" + posicion.id()
        : "traccar:" + dispositivo + ":" + fecha.toEpochMilli();
```

**Despues:**

```java
String clave = posicion.id() != null && posicion.id() > 0
        ? "traccar:" + posicion.id()
        : "traccar:" + dispositivo + ":" + fecha.toEpochMilli();
```

`id == null` o `id <= 0` se tratan como "sin id" y usan la clave de respaldo que ya existia.

Evidencia del bug (antes del cambio), 2026-09-20 17:38:

```
TRACCAR_E2E=true mvn test -Dtest=IntegracionGpsTraccarIT#n_posiciones_distintas_del_mismo_bus_quedan_registradas
BUILD FAILURE
AssertionError: No llegaron 3 filas a posiciones_historicas (hay 1)
JSON real de Traccar (reenvio): {"position":{"id":0,...}}
```

Tres posiciones distintas del mismo bus (lat/lon/fixTime distintos) quedaron en **una** fila `traccar:0`.

No se cambio `position.valid` ni el tratamiento de coordenadas 0,0. El resto de `src/main` no se toco. La app web no se toco.

## Criterio 4 — comandos para repetir a mano

No se levanto la app web en esta entrega (pedido explicito). Cuando se pida:

```bash
# 1. Backend con token (el compose raiz NO declara TRACCAR_TOKEN).
export TRACCAR_TOKEN='un-secreto-local-con-mas-de-32-caracteres'
export ECORUTA_ADMIN_TOKEN='solo-para-desarrollo-local-no-usar-en-produccion'
TRACCAR_TOKEN=$TRACCAR_TOKEN mvn spring-boot:run

# 2. Traccar 6.15.3. El contenedor debe reenviar a host.docker.internal:8080,
#    NUNCA a localhost:8080 (localhost dentro de Docker es el propio Traccar).
#    forward.url =
#    http://host.docker.internal:8080/api/v1/integraciones/traccar/posiciones
#    header X-Traccar-Token, retry on, registerUnknown=true, filter.enable=false.
#    Publicar 8082, 5001 y 5002.

# 3. Crear el equipo del BUS-01 (tiene ruta_id) y asociar el IMEI:
curl -s -X POST http://host.docker.internal:8080/api/v1/admin/equipos \
     -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"vehiculoId":1,"etiqueta":"GPS 103A demo"}'
# Respuesta: {"equipoId":<id>,"codigo":"eq_...",...}

docker compose exec -T db psql -U ecoruta -d ecoruta -c \
  "INSERT INTO dispositivos_externos (identificador, equipo_id) VALUES ('860000000000001', <id>);"

# 4. Emisor recorriendo la ruta:
java src/test/java/gt/muni/jalapa/ecoruta/herramientas/SimuladorGps.java \
     --modo traccar --protocolo tk103 --imei 860000000000001 --vueltas 0

# 5. App web (repo app-movil-buses, rama develop):
#    VITE_API_BASE_URL=http://host.docker.internal:8080  y el comando de dev del front
```

Si algo falla ahi, documentar que y por que, **sin arreglarlo**.

## Como repetir con el equipo real

1. En el rastreador (manual del TK103A/B): APN del operador, servidor = IP/host de Traccar, puerto **5002** (tk103) o **5001** (gps103). Si el clone habla gt06/h02, usar ese puerto.
2. Encender Traccar con `database.registerUnknown=true` o dar de alta el IMEI por la UI/API. `forward.url` apunta a `http://host.docker.internal:8080/api/v1/integraciones/traccar/posiciones` (no `localhost`).
3. Crear el equipo y asociar el IMEI:

   ```bash
   curl -s -X POST http://host.docker.internal:8080/api/v1/admin/equipos \
        -H "X-Admin-Token: $ECORUTA_ADMIN_TOKEN" \
        -H 'Content-Type: application/json' \
        -d '{"vehiculoId":<id-del-bus>,"etiqueta":"GPS real"}'
   ```

   ```sql
   INSERT INTO dispositivos_externos (identificador, equipo_id)
   VALUES ('<IMEI-real>', <id del equipo ACTIVO del bus>);
   ```

4. Repetir C1–C3 y C5 a mano: ver la posicion en Traccar, `GET /api/v1/telemetria/posicion?vehiculoId=<id>`, y `curl -N -H 'Accept: text/event-stream' http://host.docker.internal:8080/api/v1/telemetria/stream` **antes** del siguiente punto. Para C5, reenviar el mismo JSON capturado al adaptador con `X-Traccar-Token`.
5. Actualizar la tabla: C1 pasa a **Cumplido** solo cuando el origen sea el hardware.
