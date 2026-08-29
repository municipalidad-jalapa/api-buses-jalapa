# HU-57 — Evidencia de pruebas

**Historia:** aviso al pasajero cuando el bus está por llegar a su parada.

**Resultado:** 12/12 pruebas automatizadas, `BUILD SUCCESS`.

```text
mvn -Dtest=AvisoDeAproximacionIT,AbordajeServiceTest test
```

(Docker Desktop encendido: las de integración usan PostGIS con Testcontainers.)

---

## Criterios validados

| Criterio | Estado | Cómo se comprobó |
|----------|--------|------------------|
| AC1 — el aviso llega solo a reservas activas de esa parada | OK | Dos reservas en Parque Central y una en otra parada. Al acercarse el bus, solo las dos de Parque reciben `APROXIMACION`. |
| AC2 — no se duplica el aviso en el mismo acercamiento | OK | Entra al radio: 1 aviso. Se queda: no se repite. Sale y vuelve: segundo aviso. |
| AC3 — al llegar a la parada se manda el segundo aviso | OK | A ~100 m solo aproximación. Encima de la parada: aviso `LLEGADA`. |
| AC4 — el pasajero registra si subió | OK | `subio=true` → `ABORDO`. `subio=false` → `CANCELADA`. Segunda respuesta → 422. |
| AC5 — el dato del conductor sobrescribe al del pasajero | OK | Pasajero marca ABORDO; conductor marca que no subió → `CANCELADA`. |
| AC6 — un fallo de FCM se registra y no detiene la telemetría | OK | FCM falla en una reserva: queda fila en `fallos_de_aviso`, la otra sí avisa, la ingesta responde 202. |
| AC7 — no existe aviso de “diez pasajeros” | OK | 10 reservas lejos: 0 avisos. Al acercarse: solo `APROXIMACION`. |

Radios: aproximación 250 m, llegada 40 m. Parada de referencia: Parque Central (semilla V6).

---

## Dónde están las pruebas

- `AvisoDeAproximacionIT` — 9 pruebas de integración (API + GPS + avisos).
- `AbordajeServiceTest` — 3 pruebas unitarias de abordaje.

Demo manual (sin app ni tableta): `tools/simulador-gps.py` sobre el mismo `POST /api/v1/telemetria/posiciones`.

No se valida que el push se vea en el navegador (exige HTTPS y el front).
