# Formato real del reenvío de Traccar

Captura tomada el 2026-09-21 contra **Traccar 6.14.5** (`forward.type=json`, protocolo OsmAnd).
El cuerpo versionado está en `src/test/resources/traccar/traccar-position-sample.json`.

**No debe asumirse que el identificador del dispositivo y la posición llegan en un objeto plano.**

Traccar no envía `{ "deviceId": 1, "latitude": 14.0, "longitude": -90.0 }`.
Envía un `PositionData` con dos objetos anidados: `position` y `device`.

## Contrato HTTP

`POST /api/v1/integraciones/traccar/posiciones`

Cabeceras:

- `X-Traccar-Token`: secreto de integración (`TRACCAR_TOKEN`, mínimo 32 caracteres). No viaja en el frontend ni en el repositorio.
- `Content-Type: application/json`

Respuestas:

- **202** — reenvío procesado. Cuerpo `{ "recibidas", "aceptadas", "descartadas" }`. Un 202 no implica que todas se guardaron (ventana de 12 h o reenvío repetido).
- **400** — cuerpo mal formado o ilegible.
- **401** — cabecera ausente o token distinto del configurado.
- **422** — dispositivo sin equipo asociado, o datos semánticamente inválidos (coordenadas, velocidad, fecha). No se registra nada del reenvío.

## Configuración de Traccar (6.14+)

`forward.json=true` ya no selecciona el JSON: el tipo por defecto es URL. El reenvío real de esta historia usa:

```xml
<entry key='forward.url'>https://<api>/api/v1/integraciones/traccar/posiciones</entry>
<entry key='forward.type'>json</entry>
<entry key='forward.header'>X-Traccar-Token: <secreto-largo></entry>
```

## Mapeo de la captura

| Dato EcoRuta | Campo real | Notas |
| --- | --- | --- |
| Identificador del dispositivo | `device.uniqueId` | En la captura, el IMEI `860000000000001`. `position.deviceId` es el id interno de Traccar, no el IMEI. |
| Latitud | `position.latitude` | Grados, WGS84. Rango −90…90. |
| Longitud | `position.longitude` | Grados, WGS84. Rango −180…180. |
| Velocidad | `position.speed` | **Nudos** (unidad interna de `org.traccar.model.Position`). EcoRuta la convierte a km/h. |
| Fecha / hora del fix | `position.fixTime` | ISO-8601 con offset, p. ej. `2026-09-21T02:41:35.000+00:00` (UTC). Respaldo: `position.deviceTime`. |
| Zona horaria | offset en el instante | La captura llega con `+00:00`, no con zona `America/Guatemala`. |
| Identidad de la lectura | `position.id` | En la captura real llega **`0`**: Traccar reenvía antes de persistir. No se usa el 0 como clave; se usa `uniqueId` + epoch de la fecha. |

Campos adicionales presentes en la captura (se ignoran si EcoRuta no los usa): `protocol`, `serverTime`, `valid`, `altitude` (m), `course`, `accuracy`, `attributes` (`hdop`, `distance`, `motion`, …), y en `device`: `name`, `status`, `lastUpdate`, `positionId`.
