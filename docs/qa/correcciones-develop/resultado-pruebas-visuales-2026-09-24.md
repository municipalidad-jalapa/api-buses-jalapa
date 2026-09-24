# Resultado de pruebas visuales — correcciones de `develop`

**Fecha:** 24 de septiembre de 2026  
**Ramas probadas:** `qa-correcciones-develop` en backend y frontend  
**Entorno:** aplicación local (`http://localhost:5173`) con backend y PostgreSQL en Docker  
**Método:** inspección visual e interacción directa en navegador, API local y dos simuladores GPS.

## Resumen ejecutivo

El mapa público superó las pruebas visuales principales: responde correctamente en móvil, tableta y escritorio, no produce desplazamiento horizontal y presenta el trazado, las paradas y el bus de forma legible.

Se levantaron dos simuladores, uno para cada ruta activa:

| Ruta | Vehículo | Resultado |
|---|---|---|
| Centro de Jalapa | `BUS-01` | Transmite posiciones correctamente |
| Parque Central a Metroplaza | `BUS-02` | Transmite posiciones correctamente |

Se confirmó la regla funcional del proyecto: **cada ruta muestra un único bus asignado**. No se probaron dos vehículos dentro de una misma ruta.

No se encontraron defectos visuales bloqueantes en la fase del mapa. Las fases de reserva, ETA, notificaciones y paneles autenticados quedaron parcialmente pendientes por dependencias de prueba que no estaban disponibles.

## Resultados por fase

### Fase 1 — Mapa legible y responsive

**Resultado: PASS**

Resoluciones verificadas:

- `320 × 568`
- `360 × 740`
- `390 × 844`
- `768 × 1024`
- `1280 × 800`

Comprobaciones superadas:

- No existe desplazamiento horizontal en ninguna resolución.
- El encabezado, selector de ruta y acceso permanecen utilizables.
- En `320 px` se oculta el texto “EcoRuta”, pero se conserva el logotipo y el espacio necesario para el selector; no se considera defecto.
- El trazado tiene contraste y grosor suficientes sobre el mapa.
- Las paradas se distinguen y cuentan con nombres accesibles.
- La atribución de OpenStreetMap permanece visible.
- El panel inferior se muestra completo y puede minimizarse.
- El botón “Ver el bus” centra y amplía el mapa alrededor del vehículo.
- El mapa funciona en modo claro y oscuro.
- El selector cambia correctamente entre las dos rutas.
- Al cambiar de ruta se presenta el bus correspondiente y no el de la otra ruta.
- Los buses se actualizan mientras los simuladores transmiten.

**Correcciones necesarias:** ninguna detectada en esta fase.

### Fase 2 — Reserva de parada

**Resultado: PARCIAL / PENDIENTE DE VALIDACIÓN**

Comprobaciones superadas:

- La hoja de selección de parada es legible en `360 × 740`.
- La hoja puede minimizarse para dejar más espacio al mapa.
- Al solicitar la parada más cercana aparece el estado “Buscando dónde estás…”.
- Cuando la geolocalización no está disponible, la carga termina y se muestra un mensaje comprensible.
- El error ofrece una salida: elegir una parada directamente en el mapa.

Comprobaciones pendientes:

- Selección completa de una parada y creación de la reserva.
- Cambio de parada mediante el flujo de dos toques.
- Tarjeta de reserva próxima a vencer.
- Renovación por otros 15 minutos.
- Cancelación y expiración de una reserva.

**Qué se debe arreglar o preparar:**

1. Agregar una forma reproducible de ejecutar este escenario en QA sin depender del clic sobre marcadores Leaflet del navegador automatizado. Una opción es incluir identificadores de prueba estables (`data-testid`) o una ruta/fixture de QA para seleccionar una parada.
2. Mantener disponible una configuración de geolocalización simulada para pruebas locales y CI.
3. Ejecutar nuevamente toda la fase con una reserva real antes de aprobarla.

> No se confirmó un defecto del producto en esta fase. El bloqueo encontrado corresponde a la capacidad del entorno de automatización para accionar los marcadores del mapa.

### Fase 3 — Avisos y notificaciones push

**Resultado: BLOQUEADO**

No fue posible completar la fase porque depende de una reserva activa y, para los escenarios móviles, de un dispositivo Android real.

**Qué se debe arreglar o preparar:**

1. Completar primero el escenario de reserva de la fase 2.
2. Proporcionar un dispositivo Android real con Chrome y permisos de notificaciones.
3. Confirmar que el backend QA tenga credenciales de Firebase Admin válidas.
4. Ejecutar los escenarios con aplicación abierta, en segundo plano y con pantalla bloqueada.
5. Registrar evidencia de llegada, cancelación y ausencia de duplicados.

### Fase 4 — ETA para el pasajero

**Resultado: PARCIAL / PENDIENTE DE VALIDACIÓN**

Comprobaciones superadas:

- La API recibe posiciones recientes de ambos buses.
- El marcador de cada bus aparece y se desplaza en su ruta.
- El selector conserva el aislamiento entre rutas.

Comprobaciones pendientes:

- Visualización de la tarjeta ETA después de elegir una parada.
- Estados de cálculo, ETA disponible, dato antiguo y ETA no disponible.
- Actualización del ETA mientras el bus se desplaza.
- Comportamiento del ETA cuando el bus llega a la parada.

**Qué se debe arreglar o preparar:**

1. Desbloquear el flujo de reserva/selección de parada.
2. Añadir un escenario de QA que permita posicionar el bus antes y después de una parada concreta.
3. Repetir la prueba con un solo simulador por ruta, como exige el modelo funcional.

### Fase 5 — Panel del conductor

**Resultado: PARCIAL / BLOQUEADO POR AUTENTICACIÓN**

Comprobaciones superadas:

- El login es legible y utilizable en `390 × 844`.
- Los campos de correo y contraseña tienen etiquetas visibles.
- `/conductor` redirige correctamente a `/conductor/login` cuando no existe sesión.
- La pantalla mantiene un diseño limpio sin desbordamiento horizontal.

Comprobaciones pendientes:

- Inicio de sesión con conductor válido.
- Visualización de la ruta asignada.
- Contadores y lista de paradas.
- Cambios en reservas mientras el bus avanza.
- Estado de conductor sin ruta asignada.
- Cierre y caducidad de sesión.

**Qué se debe arreglar o preparar:**

1. Crear o documentar una cuenta QA de conductor en Firebase.
2. Asociar su `firebase_uid` con un usuario activo y una ruta en la base de QA.
3. Proveer una segunda cuenta sin ruta para validar el mensaje de ese estado.
4. No guardar contraseñas en el repositorio; distribuirlas por el canal seguro definido por el equipo.

### Fase 6 — Panel municipal

**Resultado: PARCIAL / BLOQUEADO POR AUTENTICACIÓN**

Comprobaciones superadas:

- El acceso “Municipalidad” abre la pantalla correcta.
- El login fue revisado en `1440 × 900`.
- `/admin/rutas` redirige a `/admin/login` cuando no existe sesión.
- El diseño del acceso municipal es legible y diferencia claramente el área administrativa.

Comprobaciones pendientes:

- Inicio de sesión como administrador municipal.
- Dashboard y estado de los vehículos.
- Edición y guardado de rutas.
- Validaciones del formulario de rutas.
- Persistencia después de recargar.
- Permisos y caducidad de sesión.

**Qué se debe arreglar o preparar:**

1. Proporcionar una cuenta QA de administrador municipal configurada en Firebase y en la base.
2. Documentar datos de prueba seguros para editar una ruta sin afectar información real.
3. Repetir la fase completa con sesión autenticada.
4. Verificar explícitamente que un conductor no pueda acceder al panel municipal.

## Hallazgos técnicos adicionales

- El backend inició correctamente y Flyway validó las 23 migraciones.
- Firebase Admin se inicializó correctamente en el entorno local.
- Los avisos `AsyncRequestTimeoutException` encontrados en el historial corresponden al flujo SSE y son anteriores al arranque de esta sesión; no se observó un fallo actual de la aplicación asociado con ellos.
- La interfaz debe abrirse como `http://localhost:5173`, ya que esa dirección coincide con la configuración local de API y CORS. Al usar `127.0.0.1`, la pantalla inicialmente no cargó las rutas.

## Correcciones y acciones prioritarias

| Prioridad | Acción | Tipo |
|---|---|---|
| Alta | Alinear la configuración local para que frontend, API y CORS utilicen el mismo host (`localhost` o `127.0.0.1`) | Configuración |
| Alta | Preparar cuentas QA de conductor y administrador municipal | Datos de prueba |
| Alta | Completar la fase de reserva y, a partir de ella, ETA y notificaciones | Validación funcional |
| Media | Añadir selectores estables o utilidades QA para accionar marcadores Leaflet | Testabilidad |
| Media | Preparar una configuración reproducible de geolocalización simulada | Testabilidad |
| Media | Ejecutar notificaciones push en Android real | Compatibilidad |
| Baja | Revisar los timeouts periódicos del SSE y confirmar que sean cierres esperados, no ruido innecesario en logs | Observabilidad |

## Criterio de cierre recomendado

La corrección puede considerarse aprobada visualmente para el **mapa público y su comportamiento responsive**. No debe cerrarse todavía la validación global de las seis fases hasta completar:

1. Una reserva real con renovación y expiración.
2. Todos los estados de ETA.
3. Push en Android real.
4. Panel del conductor autenticado.
5. Panel municipal autenticado y edición de rutas.

