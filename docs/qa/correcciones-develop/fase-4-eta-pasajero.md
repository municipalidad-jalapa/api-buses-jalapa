# Prompt de prueba visual — Fase 4: minutos para que llegue el bus

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta. Vas a verificar la **Fase 4** (rama `qa-correcciones-develop`,
frontend `feat(qa): minutos para que el bus llegue a la parada del pasajero`).

**URL a probar:** `<URL del entorno>` (local: `http://localhost:5173/`). Ventana 360 × 740.
El bus tiene que estar transmitiendo: en local, correr el simulador
`python tools/simulador-gps.py --api <URL del backend> --ruta 1` desde el backend.

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf, 5.1 — No implementado)

> "Falta el tiempo estimado antes de que llegue el bus a esa parada, calculado por ruta real y
> no en línea recta, con indicación explícita cuando el dato no sea confiable."

El backend ya calculaba el ETA siguiendo el trazado (`GET /api/v1/rutas/{id}/eta`); faltaba
mostrarlo al pasajero.

## Pasos

1. Tocá una parada de la ruta (o usá "Usar la parada más cercana").
   - ✅ En la hoja, bajo el nombre de la parada, aparece la caja **"Llega a tu parada en"**
     con los minutos en grande (p. ej. **"6 min"**) y tres barritas.
2. Con el bus en movimiento normal:
   - ✅ Tres barritas verdes + texto **"cálculo confiable"**.
   - ✅ El número baja a medida que el bus se acerca (se refresca cada ~20 s y con cada
     posición nueva). Compará contra `GET /api/v1/rutas/1/eta` en *Network*: el valor
     `minutos` de esa parada es el que se ve.
3. Confirmá la reserva ("Estoy esperando aquí").
   - ✅ La caja sigue visible en la hoja confirmada, entre "Ya avisamos que estás esperando"
     y "En tu parada".
4. **Dato no confiable.** Recién arrancado el simulador (sin historial de velocidad), o con el
   bus fuera del trazado:
   - ✅ Dos barritas + **"cálculo aproximado"** y el número con **"≈"** (p. ej. "≈ 9 min").
   - ✅ En desvío dice **"cálculo aproximado · el bus va por un desvío"**.
5. **Sin estimación.** Detené el simulador y esperá ~2 min (o probá en una ruta sin bus):
   - ✅ Una barrita + **"Todavía no podemos calcularlo"** con el motivo:
     "El bus no está enviando su ubicación", "El bus está detenido" o
     "La ruta no tiene bus asignado". Nunca un número inventado ni la palabra "error".
6. **Parada que el bus ya pasó** en esta vuelta: ✅ "El bus ya pasó por aquí en esta vuelta".
7. Bus a punto de llegar (0 min): ✅ dice **"llegando"**.
8. Cambiá de ruta en el selector amarillo: ✅ la caja no muestra minutos de la ruta anterior.
9. Modo oscuro: ✅ la caja se oscurece y las barritas pasan a amarillo.
10. Pantalla 320 × 568: ✅ la caja se compacta y la hoja se puede achicar con la manija.

## Resultado esperado

Tabla con los puntos 1–10 marcados ✅ / ❌, con captura y una línea de explicación para cada ❌.
