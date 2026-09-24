# Preparar el entorno para las pruebas visuales de `qa-correcciones-develop`

Seguí esta guía **antes** de cualquier prompt de fase. La ronda 1
([resultado-pruebas-visuales-2026-09-24.md](resultado-pruebas-visuales-2026-09-24.md)) se probó
contra el backend de otra rama (SCRUM-26: 23 migraciones, sin `/api/v1/conductor/panel` ni
`/api/v1/admin/rutas`), así que las fases 4–6 no podían pasar aunque hubiera cuentas.

## 1. Backend de esta rama, aislado

Esta rama llega a la migración **V16**. Una base que ya tiene V17+ (la de SCRUM-26) no le sirve:
Flyway la rechaza. Por eso se levanta como proyecto aparte, con su propia base y otros puertos,
sin tocar el stack que ya esté corriendo:

```bash
cd backend
git checkout qa-correcciones-develop
API_PUERTO=8081 DB_PUERTO=5433 docker compose -p ecoruta-qa-develop up -d --build
```

Para apagarlo: `docker compose -p ecoruta-qa-develop down` (con `-v` borra también su base).

**Comprobación obligatoria** (si falla, no sigas):

```bash
# Debe listar /api/v1/conductor/panel y /api/v1/admin/rutas
curl -s localhost:8081/v3/api-docs | grep -o '"/api/v1/\(conductor/panel\|admin/rutas\)"' | sort -u
# Debe decir 16
docker exec ecoruta-qa-develop-db-1 psql -U ecoruta -d ecoruta -tc \
  "select max(version::int) from flyway_schema_history where version is not null"
```

## 2. Frontend apuntando a ese backend

En `frontend/.env.local` (no se versiona):

```dotenv
VITE_API_BASE_URL=http://localhost:8081
# Solo npm run dev: el pasajero "está" junto a 1a Calle - Mercado, sin GPS.
VITE_UBICACION_SIMULADA=14.6326,-89.9871
```

```bash
cd frontend && npm run dev
```

Abrí `http://localhost:5173` (también funciona `http://127.0.0.1:5173`).

## 3. El bus en movimiento: un simulador por ruta

```bash
cd backend
export ECORUTA_ADMIN_TOKEN=solo-para-desarrollo-local-no-usar-en-produccion
python tools/simulador-gps.py --api http://localhost:8081 --ruta 1
python tools/simulador-gps.py --api http://localhost:8081 --ruta 2   # otra terminal
```

Para dejar el bus en un punto exacto (ETA y avisos), **detené el simulador de esa ruta** y usá:

```bash
python tools/posicionar-bus.py --api http://localhost:8081 --ruta 1 --parada 3 --metros 300   # 300 m antes
python tools/posicionar-bus.py --api http://localhost:8081 --ruta 1 --parada 3 --metros 0     # en la parada
python tools/posicionar-bus.py --api http://localhost:8081 --ruta 1 --parada 3 --metros -200  # ya pasó
python tools/posicionar-bus.py --api http://localhost:8081 --ruta 1 --parada 3 --metros 600 --velocidad 0  # detenido
```

## 4. Elegir parada sin tocar el mapa

- `http://localhost:5173/registro/{paradaId}` abre el mapa con esa parada elegida (es la URL del
  QR de la parada). Ej.: `/registro/2` → "1a Calle - Mercado".
- Selectores estables: `[data-testid="parada-{id}"]`, `[data-testid="bus"]`,
  `[data-testid="yo"]`, `[data-testid="hoja"]` (con `data-fase`: `vacia | buscando | elegida |
  confirmada`) y `[data-testid="eta"]` (con `data-nivel`: 3 confiable, 2 aproximado, 1 sin dato).

Ids de parada de la ruta 1: 1 Parque Central, 2 1a Calle - Mercado, 3 1a Calle - El Calvario,
4 1a Calle - Terminal, 5 Llano Grande… (`GET /api/v1/rutas`).

## 5. Cuentas QA

Se crean **en Firebase Console** (proyecto `seminariodbfirebase` → Authentication → Agregar
usuario, con correo y contraseña). Las contraseñas se reparten por el canal seguro del equipo;
nunca en el repo.

| Cuenta | Para | Cómo se vincula |
|---|---|---|
| Conductor con ruta | Fase 5 | `cuentas-qa.sql` (`uid_conductor`) → ruta 1 |
| Conductor sin ruta | Fase 5, punto 7 | `cuentas-qa.sql` (`uid_conductor_sin_ruta`) |
| Administrador municipal | Fase 6 | Variables del backend (abajo) |

Conductores:

```bash
docker exec -i ecoruta-qa-develop-db-1 psql -U ecoruta -d ecoruta \
  -v uid_conductor='<uid>' -v uid_conductor_sin_ruta='<uid>' \
  < docs/qa/correcciones-develop/cuentas-qa.sql
```

Administrador: en `backend/docker-compose.override.yml` (no se versiona) poné
`ECORUTA_ADMIN_FIREBASE_UID` y `ECORUTA_ADMIN_USUARIO` de la cuenta admin y reiniciá el backend
(`API_PUERTO=8081 DB_PUERTO=5433 docker compose -p ecoruta-qa-develop up -d backend`). El backend
la da de alta solo al arrancar.

## 6. Forzar estados sin esperar

| Estado | Cómo |
|---|---|
| Reserva por vencer | `UPDATE registros_espera SET expira_en = now() + interval '100 seconds' WHERE id = <id>;` |
| Bus sin datos hace horas (panel municipal) | `INSERT INTO posiciones_historicas (ubicacion, velocidad_kmh, registrado_en, vehiculo_id) VALUES (ST_SetSRID(ST_MakePoint(-89.99, 14.64), 4326), 0, now() - interval '1070 minutes', 2);` con el simulador de la ruta 2 detenido |
| Gente esperando (panel del conductor) | Reservar desde `/registro/{id}` en otras ventanas privadas, o `INSERT INTO registros_espera (dispositivo_id, parada_id, estado, creado_en, expira_en) SELECT gen_random_uuid()::text, 2, 'ACTIVA', now(), now() + interval '30 minutes' FROM generate_series(1,3);` |

(`docker exec -it ecoruta-qa-develop-db-1 psql -U ecoruta -d ecoruta` para correrlos.)

## 7. Lo que no se puede en local

- Push con la pestaña cerrada (fase 3 B y C): teléfono Android real con Chrome, HTTPS y la
  VAPID key configurada. En local sirve `http://localhost` en el propio equipo.
