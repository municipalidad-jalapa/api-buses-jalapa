# Manual de pruebas — HU Desarrollo-135 (vigencia de 5 minutos con renovación y expiración)

Este manual explica cómo ejecutar **todas** las pruebas del backend `ecoruta-backend`:
las automáticas (unitarias, de integración y de aceptación) y la prueba **manual**
end-to-end de la HU-135.

> Los archivos de prueba de esta HU están copiados en [`casos/`](casos/) para lectura.
> **Los que Maven ejecuta de verdad viven en `src/test/java/`** (Maven exige esa ruta);
> no borres los originales.

---

## 1. Requisitos del entorno

| Requisito | Detalle |
|---|---|
| **JDK 21** | El `pom.xml` fija `java.version = 21`. El `mvn` del PATH usa JDK 17 y **no compila**. Hay que apuntar `JAVA_HOME` a un JDK 21. |
| **Docker** | Las pruebas `*IT` y la suite de aceptación levantan un contenedor `postgis/postgis:17-3.5` con Testcontainers. Docker Desktop tiene que estar **corriendo**. |
| **Maven** | 3.9+ (ya instalado). Necesita acceso a internet la primera vez para bajar plugins. |
| **curl** | Para la prueba manual (incluido en Git Bash / Windows 10+). |

### Fijar el JDK 21

**Git Bash:**
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
```

**PowerShell:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
```

### Comprobar que Docker está arriba
```bash
docker info        # si falla: abrir Docker Desktop y esperar ~20 s
```

---

## 2. Pruebas automáticas

Todas se lanzan con **`mvn test`** (el pipeline usa `mvn test`, no `mvn verify`; por eso
las clases `*IT` también corren en esa fase).

Ubícate en la raíz del proyecto (`api-buses-jalapa/`).

### 2.1 Suite completa (todo el backend)
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
mvn test
```
Al final debe decir `BUILD SUCCESS` y `Tests run: 136, Failures: 0, Errors: 0`.

### 2.2 Solo lo de la HU Desarrollo-135
```bash
mvn test -Dtest='ReservaTest,DemandaServiceTest,ReservaVigenciaIT,EsquemaValidaTest'
```

### 2.3 Clase por clase

| Comando | Qué prueba | Necesita Docker |
|---|---|---|
| `mvn test -Dtest=ReservaTest` | Reglas de dominio de `Reserva`: `estaVigente`, `renovar` → `RENOVADA`, `expirar` → `EXPIRADA`, conjuntos de estados | No |
| `mvn test -Dtest=DemandaServiceTest` | Servicio con mocks: cálculo de vigencia (5 min) al crear y renovar, 404 parada inexistente, 422 dispositivo con reserva vigente, 422 al renovar expirada / no activa, barrido masivo | No |
| `mvn test -Dtest=ReservaVigenciaIT` | Integración real (HTTP + PostGIS): un caso por criterio de aceptación (ver tabla en §4) | **Sí** |
| `mvn test -Dtest=EsquemaValidaTest` | Que Flyway aplica `V7` y que Hibernate valida (`ddl-auto=validate`) que la entidad `Reserva` calza con `registros_espera` | **Sí** |
| `mvn test -Dtest=PruebasDeAceptacionTest` | Suite Gherkin de todo el proyecto (26 escenarios) | **Sí** |

### 2.4 Un solo método
```bash
mvn test -Dtest='ReservaVigenciaIT#renovar_una_reserva_ya_expirada_responde_422'
```

### 2.5 Informes
- Texto: `target/surefire-reports/*.txt`
- Aceptación (HTML + JUnit XML): `target/cucumber/reporte.html`, `target/cucumber/cucumber.xml`

---

## 3. Prueba manual end-to-end (HU-135)

Ejercita los endpoints reales contra un Postgres real, con la vigencia bajada a **1 minuto**
y el barrido cada **5 s** para no esperar 5 minutos.

### 3.1 Levantar base + aplicación
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"

# 1) Postgres
docker compose up -d db

# 2) Aplicación con vigencia y barrido acelerados
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Decoruta.demanda.vigencia-minutos=1 -Decoruta.demanda.barrido-segundos=5"
```
Espera a `Started EcoRutaApplication`. Salud: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

### 3.2 Guion (en otra terminal)

Hay un script listo: **[`prueba-manual.sh`](prueba-manual.sh)**
```bash
bash test/prueba-manual.sh
```

O paso a paso:

```bash
B=http://localhost:8080/api/v1/reservas

# 1. Crear  -> 201, estado ACTIVA, expiraEn a +1 min
curl -s -XPOST $B -H 'Content-Type: application/json' \
     -d '{"dispositivoId":"dev-manual","paradaId":1}'

# 2. Renovar la reserva 1 estando vigente  -> 200, estado RENOVADA, mismo id, nuevo expiraEn
curl -s -XPOST $B/1/renovacion

# 3. Crear otra con el MISMO dispositivo  -> 422 (ya tiene una vigente)
curl -s -XPOST $B -H 'Content-Type: application/json' \
     -d '{"dispositivoId":"dev-manual","paradaId":1}'

# 4. Renovar un id inexistente  -> 404
curl -s -XPOST $B/999999/renovacion

# 5. Crear con parada inexistente  -> 404
curl -s -XPOST $B -H 'Content-Type: application/json' \
     -d '{"dispositivoId":"dev-z","paradaId":424242}'

# 6. Body sin dispositivoId  -> 400
curl -s -XPOST $B -H 'Content-Type: application/json' -d '{"paradaId":1}'

# --- esperar ~75 s a que venza (vigencia=1min) y actúe la tarea @Scheduled (cada 5 s) ---
sleep 75

# 7. Ver el estado en BD: la tarea programada la dejó EXPIRADA sin que nadie hiciera nada
docker exec api-buses-jalapa-db-1 psql -U ecoruta -d ecoruta -tAc \
  "SELECT id, dispositivo_id, estado FROM registros_espera WHERE id = 1"

# 8. Renovar la reserva ya EXPIRADA  -> 422
curl -s -XPOST $B/1/renovacion

# 9. El mismo dispositivo crea otra  -> 201 (una EXPIRADA no bloquea ni cuenta como activa)
curl -s -XPOST $B -H 'Content-Type: application/json' \
     -d '{"dispositivoId":"dev-manual","paradaId":1}'
```

### 3.3 Swagger
Con la app arriba: `http://localhost:8080/swagger-ui.html` → sección **"Demanda — reservas"**.

### 3.4 Bajar todo
```bash
# Ctrl+C en la terminal de la app, luego:
docker compose down -v
```

---

## 4. Trazabilidad: criterio de aceptación → prueba

| # | Criterio de aceptación (HU-135) | Prueba automática | Paso manual |
|---|---|---|---|
| 1 | Al crearse, la reserva expira 5 min después y ese momento viaja en `expiraEn` | `ReservaVigenciaIT.al_crearse_la_reserva_expira_cinco_minutos_despues`, `DemandaServiceTest.crear_fija_la_expiracion_cinco_minutos_despues...` | 1 |
| 2 | Renovar una reserva vigente extiende +5 min y responde 200 con el nuevo `expiraEn` | `ReservaVigenciaIT.renovar_una_reserva_vigente_extiende_la_expiracion_y_responde_200` | 2 |
| 3 | Renovar una reserva expirada o no activa responde 422 | `ReservaVigenciaIT.renovar_una_reserva_ya_expirada_responde_422`, `...que_no_esta_activa_responde_422` | 8 |
| 4 | Un proceso programado marca `EXPIRADA` toda reserva vencida, sin intervención manual | `ReservaVigenciaIT.la_tarea_programada_marca_expirada_toda_reserva_vencida` | 7 |
| 5 | Una reserva expirada no cuenta como activa y no impide que el mismo dispositivo cree otra | `ReservaVigenciaIT.una_reserva_expirada_no_cuenta_como_activa_ni_impide_una_nueva` | 9 |
| Contrato | `POST /api/v1/reservas/{id}/renovacion` → 200 / 422 / 404 | toda `ReservaVigenciaIT` | 2, 4, 8 |
| Esquema | Índice único parcial solo sobre estados vigentes; entidad calza con la tabla | `EsquemaValidaTest`, migración `V7` | 9 + consulta a BD |

---

## 5. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `Fatal error compiling: invalid target release: 21` | `mvn` usó JDK 17 | Exportar `JAVA_HOME` a JDK 21 (§1) |
| `Could not find a valid Docker environment` | Docker Desktop apagado | Abrir Docker Desktop, esperar a que `docker info` responda |
| `Cannot access central ... in offline mode` | Falta un plugin en el `.m2` | Quitar `-o`; primera ejecución necesita internet |
| El puerto 8080 ya está en uso | Otra instancia de la app | Cerrarla o cambiar `server.port` |
| `docker exec ... psql` no encuentra el contenedor | El servicio se llama distinto | `docker compose ps` para ver el nombre real |
