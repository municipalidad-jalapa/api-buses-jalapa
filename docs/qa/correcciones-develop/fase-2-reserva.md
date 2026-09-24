# Prompt de prueba visual — Fase 2: reserva en la parada

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta (app web del bus municipal de Jalapa). Vas a verificar visualmente
las correcciones de la **Fase 2** (rama `qa-correcciones-develop`):

- frontend: `fix(qa): reserva con ubicacion en dos toques, hoja minimizable y aviso por vencer`
- backend: `fix(qa): la renovacion de la reserva da 15 minutos`

**URL a probar:** `<URL del entorno>` (local: `http://localhost:5173/`; QA: `https://qa.mibusjalapa.lat/`).
Usá ventana de **360 × 740** (DevTools → modo dispositivo) y concedé el permiso de ubicación.
Si estás lejos de Jalapa, en DevTools → *Sensors* fijá la ubicación en `14.6335, -89.9870`.

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf, 4.1)

1. "Al pulsar 'Usar mi ubicación' debe enviar a la ubicación actual de la persona, y al pulsarlo
   de nuevo enviar a la parada más cercana de la ruta."
2. "La pestaña del aviso de espera no se puede minimizar y en el teléfono abarca media pantalla."
3. "Notificar al usuario cuando el tiempo de espera (cinco minutos) esté por vencerse."
4. "Aumentar el tiempo para renovar la reserva (más de cinco minutos)." Decisión: la reserva
   nueva dura 5 min y **cada renovación da 15 min**.

## A. Botón de ubicación en dos toques

1. Sin reserva, tocá el botón redondo con la mira (abajo a la derecha, "Ver mi ubicación").
   - ✅ El mapa se centra y acerca en tu posición (punto rojo "Dónde estás").
   - ✅ Aparece una pastilla verde a la izquierda del botón: **"Tocá otra vez: parada más cercana"**.
   - ✅ El botón se vuelve amarillo con icono de pin.
   - ✅ Todavía **no** se eligió ninguna parada (la hoja sigue preguntando "¿En qué parada vas a esperar?").
2. Tocá el botón amarillo otra vez.
   - ✅ La hoja pasa a "PARADA ELEGIDA" con la parada más cercana y la distancia "a N min a pie".
   - ✅ El mapa se centra en esa parada (amarilla con rombo) por encima de la hoja.
   - ✅ El botón vuelve a la mira.
3. Tocá la mira una sola vez y esperá 6 s sin tocar nada.
   - ✅ La pastilla desaparece y el botón vuelve a la mira.
4. Con una reserva hecha (ver C), repetí el doble toque.
   - ✅ La pastilla dice **"Tocá otra vez: tu parada"** y el segundo toque centra tu parada reservada.
5. Negá el permiso de ubicación y tocá la mira: ✅ aparece un aviso entendible
   ("No pudimos saber dónde estás…"), sin error crudo.

## B. Hoja de reserva minimizable

1. Con cualquier fase de la hoja, tocá la manija gris de arriba de la hoja.
   - ✅ La hoja queda en **una sola línea**: "¿En qué parada vas a esperar?", "Parada elegida: …"
     o "Esperando en <parada>" con una pastilla amarilla **"N min"**.
   - ✅ El mapa ocupa casi toda la pantalla.
2. Tocá esa línea (o la manija): ✅ la hoja se abre de nuevo completa.
3. Con un nombre de parada largo, en 320 px de ancho: ✅ el nombre se corta con "…" pero la
   pastilla de minutos se sigue viendo entera.
4. Lector de pantalla / accesibilidad: la manija es un botón con `aria-expanded` y nombre
   "Achicar para ver el mapa" / "Mostrar tu parada".

## C. Aviso por vencer y renovación de 15 min

Para no esperar 3 minutos reales podés forzar una reserva a punto de vencer desde la consola
(solo para la prueba visual; el backend no se entera):

```js
localStorage.setItem('ecoruta_reserva', JSON.stringify({
  id: 999999, paradaId: 2, estado: 'ACTIVA',
  expiraEn: new Date(Date.now() + 100_000).toISOString(),
})); location.reload();
```

1. ✅ Con **menos de 2 minutos** la hoja muestra la tarjeta amarilla **"¿Seguís esperando?"** con
   el tiempo exacto ("Tu aviso vence en 1 min 35 s…") y dice que se alarga **15 minutos**.
2. ✅ El bloque de cifras ("En tu parada" / "MIN DE AVISO") se oculta mientras pregunta, para que
   la hoja no tape el mapa.
3. ✅ Si la hoja estaba minimizada, se abre sola.
4. En un teléfono real: ✅ vibra una vez al entrar en los 2 minutos. Con la pestaña en segundo
   plano (otra app encima) y el permiso de notificaciones concedido: ✅ llega una notificación
   del sistema "Tu aviso está por vencer".
5. Flujo real contra el backend: hacé una reserva de verdad ("Estoy esperando aquí"), esperá a
   que falten < 2 min y tocá **"Sigo esperando"**.
   - ✅ La tarjeta desaparece y "MIN DE AVISO" pasa a **15**.
   - En la API: `POST /api/v1/reservas/{id}/renovacion` responde `expiraEn` ≈ ahora + 15 min.
6. Dejá vencer una reserva: ✅ la hoja vuelve a "Parada elegida" con "Tu aviso venció…".

## Resultado esperado

Tabla con cada punto (A1–A5, B1–B4, C1–C6) marcado ✅ / ❌, con captura y una línea de
explicación para cada ❌.
