# Prompt de prueba visual — Fase 6: panel municipal (corregir rutas y formato de tiempo)

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta. Vas a verificar la **Fase 6** (rama `qa-correcciones-develop`):

- backend: `feat(qa): corregir el trazado y las paradas de una ruta desde el panel`
- frontend: `feat(qa): panel municipal con correccion de rutas y tiempo legible`

**URL a probar:** `<URL del entorno>`. Pantalla de escritorio 1440 × 900.
Necesitás una cuenta de administrador del panel municipal.

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf)

- **5.6 (No cumple):** "No se puede acceder, corregir ruta en el panel del administrador. Al
  intentar acceder, el sistema muestra 'Página no encontrada'." Causa: el menú "Acceder →
  Administrador" abría Swagger en el host del frontend, y no existía pantalla de corrección.
- **5.7 (Observación):** el tiempo desde la última posición solo salía en minutos
  ("hace 1070 min" debería ser "hace 17 h 50 min").

## A. Acceso (5.6)

1. En la pantalla del pasajero, abrí **Acceder** (arriba a la derecha).
   - ✅ La tercera opción dice **"Municipalidad — Panel municipal"** (ya no "Documentacion de la API").
   - ✅ Al tocarla abre `/admin` (login del panel), **no** "Página no encontrada".
2. Iniciá sesión como administrador.
   - ✅ En la cabecera verde aparece la navegación **"Estado del servicio" | "Corregir rutas"**,
     con la sección actual resaltada.
3. Escribí a mano `/admin/rutas` y recargá: ✅ abre "Corregir rutas" (con sesión) o el login.

## B. Formato de tiempo (5.7)

1. En **Estado del servicio**, mirá la columna **Última posición** de un bus que no transmite
   desde hace horas. (Para forzarlo en QA: insertar en `posiciones_historicas` una posición del
   bus con `registrado_en = now() - interval '1070 minutes'`.)
   - ✅ Dice **"hace 17 h 50 min"** (en rojo, porque está sin datos recientes).
   - ✅ Menos de 1 h: "hace N min"; exactamente horas: "hace 3 h"; más de un día: "hace 2 d 3 h".
   - ✅ Un bus transmitiendo: "hace menos de 1 min".

## C. Corregir rutas (5.6)

1. Entrá a **Corregir rutas**.
   - ✅ Selector **Ruta** arriba a la derecha con todas las rutas (las inactivas dicen "(inactiva)").
   - ✅ Mapa con el recorrido verde, sus **puntos** (círculos) y las **paradas** (cuadros con su número).
   - ✅ Panel lateral: **Recorrido** (N puntos) y **Paradas** en orden.
2. **Mover el recorrido:** arrastrá un punto verde a otra calle.
   - ✅ La línea se redibuja; el lateral dice "· con cambios sin guardar"; se habilitan
     **Deshacer cambios** y **Guardar recorrido**.
3. **Agregar punto:** hacé clic sobre el mapa cerca de un tramo.
   - ✅ Aparece un punto amarillo insertado en ese tramo (no al final de la línea).
   - ✅ El botón pasa a **"Quitar punto N"**; al tocarlo, desaparece.
4. **Deshacer cambios** → ✅ vuelve al recorrido guardado.
5. **Unir paradas en orden** → ✅ el recorrido pasa a ser la línea que une las paradas.
6. **Guardar recorrido** → ✅ aviso "Trazado guardado: N puntos. El tiempo estimado ya lo usa.".
   Abrí la pantalla del pasajero de esa ruta: ✅ la línea del mapa es la nueva.
7. **Corregir una parada:** tocá una parada en la lista (o en el mapa).
   - ✅ Se resalta en amarillo y abajo aparece el formulario con **Nombre de la parada** y sus
     coordenadas.
   - Cambiá el nombre y/o arrastrá la parada en el mapa → ✅ se habilita **Guardar parada**.
   - Guardá → ✅ "Parada «…» guardada."; en la pantalla del pasajero la parada aparece con el
     nombre y lugar nuevos.
   - Dejá el nombre vacío y guardá → ✅ "La parada necesita un nombre." y no se envía nada.
8. Seguridad: con una sesión de conductor, `GET /api/v1/admin/rutas` → ✅ 403.
9. Coordenadas absurdas (p. ej. latitud y longitud invertidas vía API) → ✅ 400.

## Resultado esperado

Tabla con A1–A3, B1, C1–C9 marcados ✅ / ❌, con captura y una línea de explicación para cada ❌.
