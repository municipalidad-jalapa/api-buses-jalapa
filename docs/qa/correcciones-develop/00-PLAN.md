# Plan de correcciones QA sobre `develop` (Ecoruta_DESARROLLO.pdf)

Rama de trabajo en ambos repos: `qa-correcciones-develop`, creada desde `origin/develop`.
No incluye nada de SCRUM-26 (sigue en su rama propia).

## Decisiones tomadas con el equipo

| Tema | Decisión |
|---|---|
| Migración a OpenStreetMap | Se mantiene MapLibre con tiles raster OSM; se mejora legibilidad (sin PMTiles propios). |
| Renovación de la reserva | La reserva nueva dura 5 min; cada renovación da **15 min**. |
| Momento de las notificaciones push | (1) bus por llegar / llegó, (2) reserva por vencer. |
| Corrección de ruta en el panel (5.6) | Se construye aquí, en develop: pantalla `/admin/rutas` + endpoints. |

## Observaciones de QA → fase

| Sección PDF | Estado QA | Observación | Fase |
|---|---|---|---|
| Mapa de Jalapa | Cumple c/obs. | Mapa poco legible; discrepancias en pantallas pequeñas | 1 |
| Migración OSM | No cumple | "Solo se implementó una librería" | 1 |
| 4.1 Reserva | Cumple c/obs. | "Usar mi ubicación": 1er toque → mi ubicación, 2º → parada más cercana | 2 |
| 4.1 Reserva | Cumple c/obs. | Hoja de espera no se minimiza, ocupa media pantalla | 2 |
| 4.1 Reserva | Cumple c/obs. | Avisar cuando la reserva está por vencer | 2 y 3 |
| 4.1 Reserva | Cumple c/obs. | Renovación de más de 5 min | 2 |
| 4.2 Avisos | Cumple c/obs. | Push no llega con la pestaña cerrada; definir cuándo notificar | 3 |
| 5.1 ETA | No implementado | Minutos para que el bus llegue a mi parada, por ruta real, con aviso de "no confiable" | 4 |
| 4.3 / 5.3 Panel conductor | Pendiente | Se perdió en la migración (HU-62, HU-75, HU-76) | 5 |
| 5.6 Corregir ruta | No cumple | "Página no encontrada" al entrar | 6 |
| 5.7 Tiempo en panel | Observación | "hace 1070 min" → "hace 17 h 50 min" | 6 |

## Fases

1. **Mapa legible y responsive** (frontend).
2. **Reserva: ubicación, hoja minimizable, aviso por vencer, renovación de 15 min** (frontend + backend).
3. **Notificaciones push con la pestaña cerrada** (frontend + backend).
4. **Minutos estimados de llegada para el pasajero** (frontend; backend ya expone `/api/v1/rutas/{id}/eta`).
5. **Recuperar el panel del conductor** (frontend; ramas `origin/feature/HU-Desarrollo-62-panel-conductor` y `origin/SCRUM-170-…`).
6. **Panel municipal: formato de tiempo, acceso y corrección de ruta** (frontend + backend).

Cada fase termina con pruebas automáticas en verde y un archivo
`docs/qa/correcciones-develop/fase-N-*.md` con un prompt para la prueba visual.
