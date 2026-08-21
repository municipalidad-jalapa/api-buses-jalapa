# ADR-006: App web con React en vez de app móvil nativa

**Estado:** Aceptada — Agosto 2026. Reemplaza la decisión inicial de usar Flutter.

## Contexto
El pasajero necesita ver dónde viene el bus y cuántos faltan para que salga. La decisión original
fue una app móvil en Flutter para Android e iOS.

Tres cosas la volvieron insostenible dentro del plazo:

1. **La revisión de Apple.** El primer envío estaba previsto para el 01/10 y la entrega en
   producción es el 31/10. Un rechazo consume semanas que no existen, y el permiso de ubicación
   exige justificación detallada en la ficha de la App Store.
2. **La fricción de instalación.** El pasajero tiene que descubrir la app, entrar a la tienda,
   descargarla e instalarla antes de poder ver el conteo. Cada paso pierde usuarios, y el piloto
   necesita gente usándolo desde el primer día.
3. **Distribución para pruebas.** Enseñarle el avance a la Municipalidad requería TestFlight y
   APK firmados; con una URL basta mandar un enlace.

## Decisión
Una app web con React 19 y TypeScript, construida con Vite, servida por HTTPS y diseñada
**móvil primero** (pantalla de referencia: teléfono de 360 px).

Todo lo que el producto necesita del teléfono existe en el navegador:

| Necesidad | API del navegador |
|---|---|
| Posición del pasajero para la geocerca | Geolocation API (exige HTTPS) |
| Bus en tiempo real | `EventSource` — ver [ADR-008](ADR-008-sse-en-vez-de-websocket.md) |
| Identidad anónima del dispositivo | Identificador persistido en el navegador |
| Aviso de salida | Web Push con service worker |
| Acceso de un toque | Manifiesto web e instalación en pantalla de inicio |

## Consecuencias
+ Sin revisión de tiendas: se despliega cuando el equipo decide, no cuando Apple aprueba.
+ El pasajero entra con una URL. Cero fricción de instalación.
+ Un solo código para Android e iOS, y una sola pista de trabajo en vez de dos.
+ Corregir un error en producción es un despliegue, no una actualización que cada usuario debe bajar.
- **El push en iOS es limitado**: Safari solo lo permite si el pasajero agrega la página a su
  pantalla de inicio. Hay que medir qué porcentaje del piloto lo hace y tener un plan alternativo.
- Sin recolección de posiciones en segundo plano. No afecta al producto: quien reporta la posición
  es el equipo a bordo, no el teléfono del pasajero.
- HTTPS pasa de recomendable a **obligatorio**: sin él no funcionan ni la geolocalización ni el
  service worker.

## Alternativas descartadas
**Flutter para Android e iOS**: era la decisión original. Se descartó por el riesgo de la revisión
de Apple contra la fecha de entrega y por la fricción de instalación. No había código escrito, así
que el cambio no costó trabajo perdido.

**Solo Android nativo**: elimina el problema de Apple, pero deja fuera a los pasajeros con iPhone,
y el sponsor es una municipalidad que atiende a todos sus vecinos.

**React Native**: mantiene un solo código, pero sigue pasando por las tiendas, que es justamente
el problema que se quería evitar.
