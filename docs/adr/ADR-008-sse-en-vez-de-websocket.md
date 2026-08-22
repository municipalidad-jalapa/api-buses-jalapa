# ADR-008: SSE en vez de WebSocket para el tiempo real

**Estado:** Aceptada — Julio 2026

## Contexto
El pasajero tiene que ver el marcador del bus moverse solo, sin recargar la página. Hay que elegir
cómo empuja el servidor esa posición.

El detalle que decide todo: **el tráfico en tiempo real va en una sola dirección**, del servidor al
pasajero. El pasajero no envía nada por ese canal. Sus dos acciones —registrarse en una parada y
cancelar— son peticiones REST puntuales, y la posición del bus la reporta el equipo a bordo por
lotes REST (ver [ADR-003](ADR-003-telemetria-offline-first.md)).

## Decisión
Server-Sent Events. El servidor expone `GET /api/v1/telemetria/stream` con `SseEmitter` de Spring,
y el navegador se conecta con `EventSource`, que es API nativa y no requiere ninguna librería.

Como respaldo queda `GET /api/v1/telemetria/posicion`, para cuando el stream no conecte.

## Consecuencias
+ **Reconexión automática y gratis**: `EventSource` reintenta solo si se corta la conexión. Con
  WebSocket ese código lo escribe uno, y hay que probarlo. En una ruta con cobertura irregular
  eso se ejercita todo el tiempo.
+ Es HTTP normal: atraviesa proxies, balanceadores y firewalls corporativos sin configuración
  especial, y no necesita el handshake de actualización de protocolo.
+ Del lado del servidor, `SseEmitter` viene en Spring Web. Sin dependencias nuevas, sin broker
  de mensajes, sin STOMP.
+ Del lado del cliente, cero kilobytes de librería. Cuenta para el objetivo de cargar en menos de
  3 segundos en red móvil.
- **Límite de conexiones por dominio en HTTP/1.1**: el navegador permite unas 6 por dominio, y un
  stream abierto ocupa una. Con HTTP/2 el límite desaparece, así que el proxy de producción debe
  servir HTTP/2.
- **El proxy inverso no debe hacer buffering**: si acumula la respuesta, el stream nunca llega.
  Hay que desactivarlo explícitamente (en Nginx, `proxy_buffering off`).
- Si en el futuro el pasajero necesitara enviar datos en tiempo real, este canal no sirve y habría
  que revisar la decisión. Hoy no hay ningún caso así.

## Alternativas descartadas
**WebSocket**: es la opción por defecto cuando alguien dice "tiempo real", pero acá paga un precio
sin recibir nada a cambio. Su ventaja es el canal bidireccional, que el producto no usa, y a cambio
suma reconexión manual, más configuración en el proxy y —si se usa con STOMP y un broker— bastante
más infraestructura.

**Polling cada pocos segundos**: simple y funciona, pero mueve el marcador a los saltos y multiplica
las peticiones contra el backend. Se conserva igual como **fallback**, no como mecanismo principal.

**MQTT hacia el navegador**: pensado en su momento para la ingesta del equipo a bordo. Para el
pasajero exigiría un broker con WebSocket y una librería en el cliente, para resolver algo que
`EventSource` ya hace nativo.
