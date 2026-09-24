# Prompt de prueba visual — Fase 3: avisos push con la pestaña cerrada

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).
> Los pasos B y C necesitan **un teléfono Android real** con Chrome: el agente guía y la
> persona toca el teléfono.

---

Sos tester de QA de EcoRuta. Vas a verificar la **Fase 3** (rama `qa-correcciones-develop`):

- backend: `fix(qa): avisos push que llegan con la pestana cerrada y aviso por vencer`
- frontend: `fix(qa): avisos push con la pestana cerrada`

## Preparación (obligatoria, ronda 2)

Seguí [preparar-entorno-qa.md](preparar-entorno-qa.md) y hacé su **comprobación obligatoria**:
el backend tiene que ser el de esta rama (`/api/v1/conductor/panel` y `/api/v1/admin/rutas` en
`/v3/api-docs`, Flyway = 16). En la ronda 1 se probó contra el backend de SCRUM-26 y por eso
estas fases no podían pasar.

- **Automatizable en local:** A (salvo el paso 1, que es del build de QA) y D.
- **Solo con teléfono Android real:** B y C. Para B usá `tools/posicionar-bus.py` en lugar del
  simulador: `--metros 240` dispara "El bus está por llegar" y `--metros 0` "¿Lograste subir?".
- Reserva real desde `/registro/{id}`; para C forzá el vencimiento con el `UPDATE` de la
  sección 6 de la guía.

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf, 4.2)

> "La aplicación solo genera avisos en la pantalla del navegador; si la pestaña está cerrada no
> llega notificación push." Pendiente por definir en qué momento notificar.

**Decisión del equipo:** push en dos momentos: (1) el bus está por llegar / llegó
(pregunta "¿Lograste subir?"), y (2) la reserva está por vencer (2 min antes).

**Causas encontradas:**
1. El build de QA no incluía `VITE_FIREBASE_VAPID_KEY` ni el remitente: el Dockerfile no los
   declaraba, así que la app nunca pedía token push.
2. El backend mandaba `data.tipo = APROXIMACION|LLEGADA` y la web esperaba
   `bus-cerca|confirmar-abordaje`: el aviso se descartaba.
3. El mensaje traía bloque `notification`: el SDK de Firebase lo dibujaba solo, sin los botones
   "Sí subí / No subí".
4. No existía aviso de "reserva por vencer".
5. Con el permiso ya concedido, el token no se volvía a registrar nunca.

## Requisitos previos (DevOps, antes de probar en QA)

- [ ] Variable/secreto del entorno `qa` en GitHub del frontend: `VITE_FIREBASE_VAPID_KEY`
      (Firebase → Configuración del proyecto → Cloud Messaging → *Web Push certificates*),
      y agregarla a `vars.VITE_REQUIRED_BUILD_ARGS`.
- [ ] Backend de QA con credenciales de Firebase Admin (`FIREBASE_CREDENTIALS_JSON` o
      `FIREBASE_CREDENTIALS_PATH`): sin ellas el envío queda en `fallos_de_aviso`.
- [ ] Redesplegar frontend y backend desde la rama.

## A. Verificar que el build trae la mensajería (navegador de escritorio)

1. Abrí `https://qa.mibusjalapa.lat/`, DevTools → *Sources* → buscá en el bundle
   `VITE_FIREBASE_VAPID_KEY`. ✅ Debe aparecer con un valor (no vacío).
2. Hacé una reserva ("Estoy esperando aquí"). En la hoja confirmada aparece la tarjeta
   **"Avisos — Activar avisos"**. Tocá **Activar avisos** y concedé el permiso.
   - ✅ Cambia a "Te avisamos cuando el bus ya viene para tu parada."
   - ✅ En *Network* hay un `POST /api/v1/dispositivos/notificaciones` con respuesta **204**.
   - ✅ En *Application → Service Workers* está activo `/firebase-messaging-sw.js?...messagingSenderId=...`.
3. Recargá la página con la reserva vigente. ✅ Aunque la tarjeta ya no se muestra, se repite el
   `POST /api/v1/dispositivos/notificaciones` (registro silencioso del token).

## B. Push "el bus está por llegar" con la pestaña cerrada (teléfono Android)

1. En el teléfono: reservá en una parada y activá avisos (paso A2).
2. **Cerrá la pestaña** (o cerrá Chrome desde recientes) y bloqueá el teléfono.
3. Llevá el bus (o el simulador de recorrido, HU-66) a menos de 250 m de esa parada.
   - ✅ Llega una notificación del sistema **"El bus está por llegar"** —
     "El bus se acerca a <parada>. Preparate."
4. Seguí hasta menos de 40 m.
   - ✅ Llega **"¿Lograste subir al bus?"** con los botones **Sí subí** / **No subí**; no se
     cierra sola.
   - Tocá **Sí subí** → ✅ se abre EcoRuta y la reserva queda como abordada (tarjeta de abordaje
     resuelta). En la base: `registros_espera.subio = true`.

## C. Push "tu aviso está por vencer" (teléfono Android)

1. Reservá y activá avisos. Cerrá la pestaña.
2. Esperá ~3 min (la reserva nueva dura 5 min; el aviso sale con 2 min o menos).
   Para acelerar en QA: `UPDATE registros_espera SET expira_en = now() + interval '90 seconds' WHERE id = <id>;`
   - ✅ En ≤ 20 s llega **"Tu aviso está por vencer"** — "¿Seguís esperando en <parada>?…"
   - ✅ Llega **una sola vez** (no se repite en cada barrido).
3. Tocá la notificación → ✅ se abre EcoRuta con la tarjeta amarilla **"¿Seguís esperando?"**.
   Tocá **Sigo esperando** → ✅ "MIN DE AVISO" pasa a 15.
4. Esperá a que vuelvan a faltar 2 min del nuevo vencimiento → ✅ llega otra vez el aviso.

## D. Sin permisos / sin Firebase

1. Tocá **Seguir sin dar permisos** → ✅ la reserva sigue funcionando; no se vuelve a ofrecer.
2. Con el backend sin credenciales de Firebase: ✅ nada se rompe; en la base aparecen filas en
   `fallos_de_aviso` con el motivo.

## Resultado esperado

Tabla con A1–A3, B1–B4, C1–C4, D1–D2 marcado ✅ / ❌, con captura (o foto del teléfono) y una
línea de explicación para cada ❌.
