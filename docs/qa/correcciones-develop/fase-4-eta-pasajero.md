# Prompt de prueba visual — Fase 4: minutos para que llegue el bus

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta. Vas a verificar la **Fase 4** (rama `qa-correcciones-develop`,
frontend `feat(qa): minutos para que el bus llegue a la parada del pasajero`).

**URL a probar:** `<URL del entorno>` (local: `http://localhost:5173/`). Ventana 360 × 740.
El bus tiene que estar transmitiendo: en local, correr el simulador
`python tools/simulador-gps.py --api <URL del backend> --ruta 1` desde el backend.

## Preparación (obligatoria, ronda 2)

Seguí [preparar-entorno-qa.md](preparar-entorno-qa.md) y hacé su **comprobación obligatoria**:
el backend tiene que ser el de esta rama (`/api/v1/conductor/panel` y `/api/v1/admin/rutas` en
`/v3/api-docs`, Flyway = 16). En la ronda 1 se probó contra el backend de SCRUM-26 y por eso
estas fases no podían pasar.

- Parada: `http://localhost:5173/registro/5` (Llano Grande). Caja del ETA:
  `[data-testid="eta"]`, con `data-nivel` 3 (confiable), 2 (aproximado) o 1 (sin estimación).
- Detené el simulador de la ruta 1 y mové el bus con `tools/posicionar-bus.py` (ver cada paso).

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf, 5.1 — No implementado)

> "Falta el tiempo estimado antes de que llegue el bus a esa parada, calculado por ruta real y
> no en línea recta, con indicación explícita cuando el dato no sea confiable."

El backend ya calculaba el ETA siguiendo el trazado (`GET /api/v1/rutas/{id}/eta`); faltaba
mostrarlo al pasajero.

## Pasos

1. Tocá una parada de la ruta (o usá "Usar la parada más cercana").
   - ✅ En la hoja, bajo el nombre de la parada, aparece la caja **"Llega a tu parada en"**
     con los minutos en grande (p. ej. **"6 min"**) y tres barritas.
2. **Confiable.** `python tools/posicionar-bus.py --api http://localhost:8081 --ruta 1 --parada 5 --metros 600`
   - ✅ Tres barritas verdes + **"cálculo confiable"** (`data-nivel="3"`).
   - ✅ Repetilo con `--metros 300`: en ≤ 20 s el número baja. Compará contra
     `GET /api/v1/rutas/1/eta` en *Network*: el `minutos` de la parada 5 es el que se ve.
3. Confirmá la reserva ("Estoy esperando aquí").
   - ✅ La caja sigue visible en la hoja confirmada, entre "Ya avisamos que estás esperando"
     y "En tu parada".
4. **Aproximado.** Esperá 3 min sin mover el bus y corré
   `… --parada 5 --metros 400 --velocidad 4` (velocidad por debajo del mínimo: no hay historial
   propio y se usa la velocidad promedio).
   - ✅ Dos barritas + **"cálculo aproximado"** y el número con **"≈"** (`data-nivel="2"`).
5. **Sin estimación.** No mandes posiciones durante 2 min.
   - ✅ Una barrita + **"Todavía no podemos calcularlo"** y **"El bus no está enviando su
     ubicación"** (`data-nivel="1"`). Nunca un número inventado ni la palabra "error".
6. **En la parada:** `… --parada 5 --metros 0` → ✅ **"llegando"** o **"1 min"**.
7. **Ya pasó:** `… --parada 5 --metros -200` → ✅ la parada vuelve a contar para la **próxima
   vuelta** (minutos altos): el recorrido es un circuito.
8. Cambiá de ruta en el selector amarillo: ✅ la caja no muestra minutos de la ruta anterior.
9. Modo oscuro: ✅ la caja se oscurece y las barritas pasan a amarillo.
10. Pantalla 320 × 568: ✅ la caja se compacta y la hoja se puede achicar con la manija.

## Resultado esperado

Tabla con los puntos 1–10 marcados ✅ / ❌, con captura y una línea de explicación para cada ❌.
