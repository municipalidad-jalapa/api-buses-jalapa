# Manual de pruebas — HU Desarrollo-95 (endurecer la seguridad de la API)

Este manual explica cómo ejecutar **todas** las pruebas del backend `ecoruta-backend`
para esta HU: las automáticas (unitarias e integración) y las **manuales** end-to-end,
tanto simuladas (con `curl` contra una app real en local) como la explicación de qué
mirar si se quisiera repetir esto con datos/tráfico reales de producción.

> Los archivos de prueba nuevos o modificados de esta HU están copiados en
> [`casos/`](casos/) para lectura. **Los que Maven ejecuta de verdad viven en
> `src/test/java/`** (Maven exige esa ruta); no borres los originales.

Criterios de aceptación de la HU (Jira, Desarrollo-95):
1. Límite de peticiones por dispositivo y por IP en los endpoints públicos.
2. CORS restringido a los orígenes conocidos.
3. Cabeceras de seguridad configuradas y verificadas.
4. Un dispositivo no puede registrar demanda a un ritmo imposible para una persona.

---

## 1. Requisitos del entorno

| Requisito | Detalle |
|---|---|
| **JDK 21+** | El `pom.xml` fija `java.version = 21`. Un JDK 17 o menor **no compila**; hay que apuntar `JAVA_HOME` a un JDK 21 o más nuevo (el equipo compila en local con JDK 22/24, ver comentario en `pom.xml`). |
| **Docker** | Las pruebas `*IT` levantan un contenedor `postgis/postgis:17-3.5` con Testcontainers. Docker Desktop tiene que estar **corriendo** (`docker info` no debe fallar). |
| **Maven** | 3.9+. |
| **curl** | Para las pruebas manuales (incluido en Git Bash / Windows 10+). |

### Fijar el JDK

**Git Bash:**
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-22"     # o cualquier JDK 21+ instalado
```

**PowerShell:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
```

### Comprobar que Docker está arriba
```bash
docker info        # si falla: abrir Docker Desktop y esperar ~20-40 s
```

---

## 2. Pruebas automáticas

Todas se lanzan con **`mvn test`**. Ubícate en la raíz del proyecto (`api-buses-jalapa/`).

### 2.1 Suite completa (todo el backend)
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-22"
mvn test
```
Al final debe decir `BUILD SUCCESS`.

### 2.2 Solo lo de esta HU
```bash
mvn test -Dtest='LimitadorDeVentanaFijaTest,RateLimitFilterTest,RateLimitPorIpIT,RateLimitPorDispositivoIT,SecurityHeadersIT,ReservaServiceTest,ReservaRenovacionTest,ReservaCancelacionTest,CrearReservaIT,ReservaVigenciaIT,CorsIT'
```

### 2.3 Clase por clase

| Comando | Qué prueba | Necesita Docker |
|---|---|---|
| `mvn test -Dtest=LimitadorDeVentanaFijaTest` | El contador de ventana fija (permite hasta la capacidad, rechaza al superarla, `Retry-After`, cupos independientes por clave, reinicio de ventana, limpieza) | No |
| `mvn test -Dtest=RateLimitFilterTest` | El filtro aislado: rutas no protegidas nunca se limitan, 429 con el cuerpo `ApiError` correcto, IPs y dispositivos con cupos independientes, sin cabecera de dispositivo solo aplica el límite de IP, `habilitado=false` desactiva todo | No |
| `mvn test -Dtest=RateLimitPorIpIT` | Límite por IP contra la app real: 429 al superarlo, IPs distintas no se contagian, una ruta pública fuera de la lista (`/actuator/health`) nunca se limita, un 429 conserva las cabeceras CORS, `DELETE` también está cubierto | **Sí** |
| `mvn test -Dtest=RateLimitPorDispositivoIT` | Límite por dispositivo contra la app real: 429 al superarlo, otro dispositivo no se contagia, sin `X-Dispositivo-Id` el límite de dispositivo no aplica | **Sí** |
| `mvn test -Dtest=SecurityHeadersIT` | Cabeceras de seguridad contra la app real: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Content-Security-Policy` (solo en `/api/**`, no rompe swagger-ui), `Strict-Transport-Security` (solo en conexión segura), y que un error también las trae | **Sí** |
| `mvn test -Dtest=ReservaServiceTest` | Reglas de `ReservaService` con mocks, incluida la nueva: un dispositivo con una creación reciente no puede crear otra aunque no tenga reserva vigente, la ventana usa la configuración y no un número fijo | No |
| `mvn test -Dtest=CrearReservaIT` | `POST /api/v1/reservas` contra PostGIS real, incluida la nueva: tras cancelar/expirar/abordar, si fue hace instantes, responde 422 por ritmo | **Sí** |
| `mvn test -Dtest=ReservaVigenciaIT` | HU-135, preexistente. Un caso (reserva expirada no cuenta como activa) recreaba una reserva del mismo dispositivo al instante; con la regla nueva del ritmo eso ahora da 422, así que el caso se ajustó para crear la reserva original fuera de la ventana de ritmo — sigue probando lo que probaba antes (el estado, no el ritmo) | **Sí** |
| `mvn test -Dtest=CorsIT` | CORS (SCRUM-274, sin cambios en esta HU): preflight permitido desde QA y producción, rechazado desde un origen no autorizado, cabeceras expuestas | **Sí** |

### 2.4 Informes
- Texto: `target/surefire-reports/*.txt`

---

## 3. Pruebas manuales end-to-end

Dos maneras de mirar lo mismo:

- **Simulada, con `curl` y límites bajados** (la práctica: nadie va a mandar 300
  peticiones a mano). Es lo que hace [`prueba-manual.sh`](prueba-manual.sh).
- **Con "datos reales"**, es decir, con los límites que trae `application.yml` por
  defecto y sin acelerar nada: se explica en la sección 3.5 qué observar y por qué
  no es práctico repetir el mismo guion tal cual (300 peticiones/min por IP tardan
  un minuto real en agotarse).

### 3.1 Por qué dos fases

El límite por IP es **una sola bolsa compartida por todas las rutas públicas**
(así lo pide el criterio: "límite de peticiones por IP", no "por IP y por ruta").
Si una sola instancia de la app tuviera a la vez capacidad de IP baja Y se le
mandaran también las peticiones de cabeceras, CORS, dispositivo y ritmo, la
sección de IP agotaría la bolsa antes de llegar a las demás y todo lo de ahí en
adelante daría 429 por la razón equivocada. Por eso, igual que
`RateLimitPorIpIT` y `RateLimitPorDispositivoIT` en el suite automático, la
prueba manual corre en **dos fases**, cada una contra su propia instancia:

| Fase | `por-ip-capacidad` | `por-dispositivo-capacidad` | Qué prueba |
|---|---|---|---|
| `ip` | 5 (baja) | 1000 (alta, no interfiere) | Límite por IP + que una ruta pública fuera de la lista protegida no se ve afectada |
| `resto` | 1000 (alta, no interfiere) | 3 (baja) | Cabeceras, CORS, límite por dispositivo, ritmo humano |

### 3.2 Fase `ip`

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-22"

# 1) Postgres
docker compose up -d db

# 2) Aplicación con capacidad de IP baja
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Decoruta.rate-limit.por-ip-capacidad=5 -Decoruta.rate-limit.por-dispositivo-capacidad=1000 -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
```
Espera a `Started EcoRutaApplication`. Salud: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

En otra terminal:
```bash
FASE=ip bash test/prueba-manual.sh
```
Al final debe decir `resumen fase=ip: 0 fallo(s)` / `TODO OK`.

Detiene la app (`Ctrl+C`) antes de pasar a la fase 2.

### 3.3 Fase `resto`

```bash
# La base ya está arriba de 3.2; si no: docker compose up -d db

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Decoruta.rate-limit.por-ip-capacidad=1000 -Decoruta.rate-limit.por-dispositivo-capacidad=3 -Decoruta.demanda.ritmo-minimo-segundos=5 -Decoruta.cors.origenes-permitidos=https://qa.buses.jalapa.gob.gt"
```

En otra terminal:
```bash
FASE=resto bash test/prueba-manual.sh
```
Al final debe decir `resumen fase=resto: 0 fallo(s)` / `TODO OK`.

Si el puerto 8080 ya está en uso en tu máquina (pasó durante el desarrollo de esta
HU: un listener de Oracle lo tenía tomado), agregá `-Dserver.port=8081` a los
`jvmArguments` de ambas fases y `BASE_URL=http://localhost:8081` antes de
`FASE=... bash test/prueba-manual.sh`.

### 3.4 Swagger
Con cualquiera de las dos fases arriba: `http://localhost:8080/swagger-ui.html`
(o el puerto que hayas usado). Útil para probar a mano cualquier endpoint público
sin armar el `curl` completo, y para comprobar visualmente que la propia UI de
swagger sigue funcionando con la CSP nueva (criterio 3): si se rompiera, no
cargaría ni el HTML.

### 3.5 Con los valores por defecto ("datos reales", sin acelerar nada)

`application.yml` trae, sin tocar nada: `por-ip-capacidad: 300` cada 60 s,
`por-dispositivo-capacidad: 60` cada 60 s, `ritmo-minimo-segundos: 5`. Son los valores
pensados para tráfico real de pasajeros, no para una demo — por eso 3.2-3.3 los bajan.
Para verificar el comportamiento real sin acelerar nada:

- **Ritmo humano (criterio 4)** se puede probar tal cual, sin tocar nada: la sección
  de ritmo de `prueba-manual.sh` (crear, cancelar, crear de nuevo al instante) da 422
  igual con `ritmo-minimo-segundos` en su valor real de producción (5 s), porque el
  guion ya actúa en menos de un segundo.
- **Límite por IP/dispositivo (criterio 1)** con los valores reales significa mandar
  300 (o 60) peticiones seguidas antes del minuto; se puede reproducir subiendo el
  rango de los `for` de `prueba-manual.sh` (`for i in $(seq 1 301)`, etc.) contra la
  app SIN los `-Decoruta.rate-limit...` de 3.2/3.3, y midiendo que la petición 301 (o
  la 61 con `X-Dispositivo-Id`) cae en 429. No se deja como paso por defecto porque
  tarda más y no prueba nada que los pasos acelerados no prueben ya: la lógica es la
  misma ventana fija, solo cambia el número.
- **CORS y cabeceras (criterios 2 y 3)** no dependen de ningún contador: la fase
  `resto` se comporta igual con o sin acelerar nada.

---

## 4. Trazabilidad: criterio de aceptación → prueba

| # | Criterio de aceptación (Desarrollo-95) | Prueba automática | Paso manual |
|---|---|---|---|
| 1 | Límite de peticiones por IP en los endpoints públicos, con 429 al superarlo | `RateLimitPorIpIT.supera_el_limite_por_ip_...`, `RateLimitFilterTest.una_ruta_protegida_responde_429_...`, `LimitadorDeVentanaFijaTest` | fase `ip`, sección "límite por IP" |
| 1 | Límite de peticiones por dispositivo, independiente del de IP | `RateLimitPorDispositivoIT`, `RateLimitFilterTest.dispositivos_distintos_tienen_cupos_independientes...` | fase `resto`, sección "límite por dispositivo" |
| 1 | Una ruta pública que no es de negocio (`/actuator/health`) no se ve afectada | `RateLimitPorIpIT.una_ruta_publica_fuera_de_la_lista_protegida_no_se_limita` | fase `ip`, sección "ruta pública fuera de la lista protegida" |
| 2 | CORS: preflight permitido desde orígenes conocidos, rechazado desde uno ajeno | `CorsIT` (SCRUM-274, sin cambios) | fase `resto`, sección "CORS" |
| 3 | Cabeceras de seguridad presentes y correctas en una respuesta pública | `SecurityHeadersIT.una_respuesta_publica_trae_las_cabeceras_de_seguridad` | fase `resto`, sección "cabeceras de seguridad" |
| 3 | La CSP no rompe swagger-ui | `SecurityHeadersIT.la_csp_no_se_aplica_fuera_de_la_api_...` | fase `resto`, sección "cabeceras de seguridad" + 3.4 (swagger a mano) |
| 3 | HSTS solo en conexión segura | `SecurityHeadersIT.hsts_solo_viaja_en_una_conexion_...` | — (MockMvc simula HTTPS; en local sin TLS no hay paso manual equivalente) |
| 4 | Un dispositivo no puede registrar demanda (crear reserva) a un ritmo imposible, aunque cancele entre intento e intento | `ReservaServiceTest.dispositivo_que_creo_una_reserva_hace_poco_no_puede_crear_otra...`, `CrearReservaIT.dispositivo_que_creo_una_reserva_hace_instantes_no_puede_crear_otra...` | fase `resto`, sección "ritmo imposible para una persona" |
| 4 | Pasado el ritmo mínimo, el mismo dispositivo sí puede volver a crear | `ReservaServiceTest.dispositivo_sin_creaciones_recientes_si_puede_crear_otra` | fase `resto`, último paso de "ritmo imposible para una persona" |
| Contrato | 429 responde con el mismo formato `ApiError` que el resto de la API, y con `Retry-After` | `RateLimitPorIpIT`, `RateLimitPorDispositivoIT`, `RateLimitFilterTest` | fase `ip` y fase `resto` |

---

## 5. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `Fatal error compiling: invalid target release: 21` | `mvn` usó un JDK menor a 21 | Exportar `JAVA_HOME` a un JDK 21+ (§1) |
| `Could not find a valid Docker environment` | Docker Desktop apagado | Abrir Docker Desktop, esperar a que `docker info` responda |
| `Cannot access central ... in offline mode` | Falta un plugin en el `.m2` | Quitar `-o`; primera ejecución necesita internet |
| El puerto 8080 ya está en uso | Otra instancia de la app, u otro servicio de la máquina (pasó con un listener de Oracle durante el desarrollo de esta HU) | Cerrarla, o agregar `-Dserver.port=8081` a `spring-boot.run.jvmArguments` y `BASE_URL=http://localhost:8081` al correr `prueba-manual.sh` |
| Una sección que debía dar 429 da 200, o una que no debía dar 429 sí lo da | Se corrió la fase equivocada, o la app se levantó sin los `-Decoruta.rate-limit...` de esa fase | Revisar que `FASE` (env var) y los argumentos de `spring-boot:run` coincidan (§3.1-3.3) |
| La sección de ritmo no cuadra con los ids | Ya había reservas de una corrida anterior en la BD | `docker compose down -v` y volver a levantar la base (borra los datos) |
