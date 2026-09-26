# Prompt de prueba visual — Fase 5: panel del conductor

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta. Vas a verificar la **Fase 5** (rama `qa-correcciones-develop`):

- backend: `feat(qa): panel del conductor con su ruta, reservas, ETA y atencion`
- frontend: `feat(qa): recuperar el panel del conductor`

**URL a probar:** `<URL del entorno>/conductor/login`. Ventana 390 × 844 (el panel se usa en un
teléfono en soporte) y después 1280 × 800.

**Requisitos:** una cuenta de conductor en Firebase cuyo `usuarios.firebase_uid` coincida y con
`usuarios.ruta_id` asignado (en desarrollo, `conductor1` → ruta 1). El bus transmitiendo
(simulador: `python tools/simulador-gps.py --api <backend> --ruta 1`). Algunas reservas activas
en paradas de esa ruta (hacelas desde otro navegador en la pantalla del pasajero).

## Preparación (obligatoria, ronda 2)

Seguí [preparar-entorno-qa.md](preparar-entorno-qa.md) y hacé su **comprobación obligatoria**:
el backend tiene que ser el de esta rama (`/api/v1/conductor/panel` y `/api/v1/admin/rutas` en
`/v3/api-docs`, Flyway = 16). En la ronda 1 se probó contra el backend de SCRUM-26 y por eso
estas fases no podían pasar.

- Cuentas: conductor con ruta y conductor sin ruta vinculados con
  [cuentas-qa.sql](cuentas-qa.sql) (sección 5 de la guía).
- Gente esperando: reservas desde `/registro/{id}` en ventanas privadas, o el `INSERT` de la
  sección 6 de la guía.

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf, 4.3 y 5.3 — Pendiente)

> "Se perdió durante la migración; debe volver a subirse." Referencia: HU-62 (reservas por
> parada), HU-75 (ETA por parada) y HU-76 (marcar que los pasajeros abordaron).

Además se corrigió un bug: con la sesión real (uid de Firebase) marcar una parada respondía
siempre 403, porque la asignación de ruta se buscaba solo por `username`.

## Pasos

1. Iniciá sesión como conductor.
   - ✅ Se abre `/conductor` con el **nombre de la ruta** como título (no "Pantalla del conductor").
2. Resumen de arriba:
   - ✅ **Esperando en total** (suma de reservas de paradas pendientes).
   - ✅ **Próxima parada** (la pendiente a la que el bus llega primero).
   - ✅ **El bus**: "En ruta", "Detenido", "Fuera del trazado" o "Sin ubicación".
3. Lista de paradas:
   - ✅ En el **orden del recorrido**, con el número de orden en un círculo verde.
   - ✅ Cada fila: nombre, estado **Pendiente**, minutos para llegar (p. ej. "6 min", o
     "≈ 9 min" si es aproximado, "Llegando", "Sin ubicación del bus") y **N ESPERANDO** en grande.
   - ✅ La próxima parada tiene la etiqueta amarilla **PRÓXIMA PARADA** y borde verde.
4. **Se actualiza solo:** desde el navegador del pasajero reservá en una parada. ✅ En ≤ 15 s el
   número de esperando de esa parada sube sin recargar. La hora "Último dato recibido" avanza.
5. **Marcar atendida (HU-76):** en una parada con gente esperando tocá **Marcar atendida**.
   - ✅ Aparecen **"Sí, ya subieron"** y **"Volver"** (no marca con un solo toque).
   - Tocá **Volver** → ✅ no pasa nada.
   - Tocá de nuevo y **Sí, ya subieron** → ✅ aviso verde "Listo: N pasajeros abordaron.";
     la fila pasa a punteada, gris, **"Atendida HH:MM"**, "Ya pasaste por aquí", sin botón.
   - ✅ En el navegador del pasajero de esa parada, en ≤ 20 s (o al volver a la pestaña) la hoja
     dice **"Buen viaje. Tu reserva quedó cerrada."**
6. Marcá la misma parada desde otra pestaña (409): ✅ "Esta parada ya estaba marcada como
   atendida." y la lista se refresca.
7. Conductor **sin ruta asignada** (`usuarios.ruta_id = NULL`): ✅ mensaje
   "No tenés una ruta asignada. Pedile a la Municipalidad que te asigne una."
8. Sin red (DevTools → Offline): ✅ se conserva el último panel y aparece el aviso de error con
   "Intentar de nuevo".
9. Accesibilidad / legibilidad: ✅ textos grandes, botones ≥ 52 px de alto, atendida/pendiente
   siempre con texto (no solo color). Modo oscuro: ✅ números y botones en amarillo.
10. 320 px de ancho: ✅ los minutos pasan debajo del nombre, sin scroll horizontal.

**Limitación conocida:** una parada se puede marcar atendida **una vez por día de servicio**
(índice único `paradas_atendidas (ruta, parada, fecha)` de HU-76). En una segunda vuelta del
mismo día la parada sigue apareciendo como atendida.

## Resultado esperado

Tabla con los puntos 1–10 marcados ✅ / ❌, con captura y una línea de explicación para cada ❌.
