# Manual de pruebas — Vistas separadas para el pasajero y el personal (backend)

## Historia

Informe de QA, panel conductor:

> Arreglar las vistas del panel, que una persona que no sea parte de
> administración o conductor no sea posible ver las opciones de inicio de sesión
> como una persona normal (que tengan URL distintos).

La separación de vistas se hace en el frontend (`/` para el pasajero y `/tools`
para el personal; ver `app-movil-buses/test/QA-vistas-separadas-tools`). En el
backend **no se cambió ningún endpoint**: la API ya estaba separada por la parte
final de la URL y cada grupo exige su rol. Esta historia agrega la prueba que lo
garantiza, para que ninguna regresión abra una ruta del personal a otro rol.

| Entorno | Inicio de sesión (abierto) | Rutas protegidas | Rol exigido |
|---|---|---|---|
| Conductor | `POST /api/v1/auth/conductor` | `/api/v1/conductor/**` | `CONDUCTOR` |
| Panel municipal | `POST /api/v1/auth/admin` | `/api/v1/admin/**`, `/api/v1/panel/**` | `ADMIN` (el SuperAdmin también lo lleva) |
| Administración del sistema | el mismo del admin | `/api/v1/superadmin/**` | `SUPERADMIN` |

Dónde vive la regla: `seguridad/SecurityConfig.java` (`hasRole` por grupo y
`denyAll()` para todo lo demás) y los filtros `AdminJwtAuthFilter`,
`ConductorJwtAuthFilter` y `PasajeroJwtAuthFilter`: cada uno solo acepta un JWT
con su claim `rol`.

## Criterio que prueba el backend

**C3**: la seguridad no depende de la vista. Cada grupo de rutas solo se abre
con su rol: sin credencial la respuesta es 401 y con la credencial de otro rol,
403. El inicio de sesión del personal sigue alcanzable sin credencial.

---

## 1. Requisitos

| Requisito | Detalle |
|---|---|
| **Docker** | Corriendo (`docker info` no debe fallar). Las pruebas `*IT` levantan PostGIS con Testcontainers. |
| **JDK 21+ y Maven 3.9+** | Solo si se corre Maven en la máquina. Si no están instalados, usar la opción B (Maven dentro de Docker). |

## 2. Pruebas automáticas

### Opción A: Maven instalado en la máquina

```bash
# Solo la de esta historia (22 casos)
mvn test -Dtest=AccesoPorRolIT

# La de esta historia junto con las de autenticación existentes
mvn test -Dtest='AccesoPorRolIT,AccesoPanelConductorIT,AuthAdminIT,AuthConductorIT'

# Suite completa
mvn test
```

### Opción B: sin Maven ni JDK (Maven dentro de Docker)

Desde la raíz del repo, en **Git Bash**:

```bash
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd -W):/app" -v ecoruta-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -w /app maven:3.9-eclipse-temurin-21 \
  mvn -B test -Dtest=AccesoPorRolIT
```

Para la suite completa, quitar `-Dtest=AccesoPorRolIT`. El volumen
`ecoruta-m2` guarda las dependencias: la primera corrida tarda más.

Los resultados quedan en `target/surefire-reports/`.

### Cómo funciona `AccesoPorRolIT`

Pide una subruta que **no existe** en cada grupo (`/api/v1/conductor/no-existe`,
etc.). Si la seguridad deja pasar, la respuesta es **404** (no hay handler); si
no, es **401** (sin credencial) o **403** (otro rol). Así el resultado no
depende de la lógica de negocio (ruta asignada, cuenta existente, etc.).

Matriz esperada:

| Quién \ Ruta | `/conductor/**` | `/admin/**` | `/panel/**` | `/superadmin/**` |
|---|---|---|---|---|
| anónimo | 401 | 401 | 401 | 401 |
| pasajero | 403 | 403 | 403 | 403 |
| conductor | **404** (pasa) | 403 | 403 | 403 |
| admin | 403 | **404** (pasa) | **404** (pasa) | 403 |
| superadmin | 403 | **404** (pasa) | **404** (pasa) | **404** (pasa) |

Y `POST /api/v1/auth/conductor` y `POST /api/v1/auth/admin` con cuerpo vacío
responden **400** (llegan a la validación, no los frena la seguridad).

## 3. Prueba manual contra la app real (simulada)

Con el backend levantado (`docker compose up -d`). Genera JWT firmados con el
secreto de desarrollo de `docker-compose.yml` para cada rol y los prueba contra
rutas reales.

Guardar como `matriz.mjs` (fuera del repo) y correr `node matriz.mjs`:

```js
import crypto from 'node:crypto';
const S = 'solo-para-desarrollo-local-no-usar-en-produccion'; // JWT_SECRET de docker-compose.yml
const b = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const tok = (rol) => {
  const n = Math.floor(Date.now() / 1000);
  const h = b({ alg: 'HS256' }) + '.' + b({ sub: 'prueba-' + rol, rol, iat: n, exp: n + 600 });
  return h + '.' + crypto.createHmac('sha256', S).update(h).digest('base64url');
};
const quien = { anonimo: null, conductor: tok('conductor'), admin: tok('admin'),
                superadmin: tok('superadmin'), pasajero: tok('pasajero') };
const rutas = ['/api/v1/conductor/panel', '/api/v1/admin/servicio',
               '/api/v1/panel/rutas', '/api/v1/superadmin/cuentas'];
console.log('ruta'.padEnd(30), Object.keys(quien).join('\t'));
for (const r of rutas) {
  const f = [];
  for (const t of Object.values(quien)) {
    const res = await fetch('http://127.0.0.1:8080' + r,
      { headers: t ? { Authorization: 'Bearer ' + t } : {} });
    f.push(res.status);
  }
  console.log(r.padEnd(30), f.join('\t'));
}
```

Resultado esperado:

```
ruta                           anonimo  conductor  admin  superadmin  pasajero
/api/v1/conductor/panel        401      403*       403    403         403
/api/v1/admin/servicio         401      403        200    200         403
/api/v1/panel/rutas            401      403        200    200         403
/api/v1/superadmin/cuentas     401      403        403    200         403
```

\* El conductor **sí** pasa la seguridad: su 403 es de negocio ("No tenés una
ruta asignada"), porque el usuario de prueba no existe. El de los demás roles
dice "La credencial presentada no autoriza esta operacion". Para distinguirlos,
mirar el `message` de la respuesta.

> En Windows usar `127.0.0.1` y no `localhost`: si WSL tiene reenvío de puertos
> activo, `localhost` puede resolverse a IPv6 (`::1`) y no llegar al contenedor.
