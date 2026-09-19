# Manual de ejecución de pruebas — Resumen de ruta (SCRUM-284 / HU Desarrollo-123)

Cómo correr las pruebas del endpoint `GET /api/v1/rutas/{rutaId}/resumen` y del
resto del proyecto.

---

## 1. Requisitos

| Requisito | Detalle |
|---|---|
| **JDK 21** | Temurin/Adoptium 21. `JAVA_HOME` debe apuntar ahí. El proyecto fija `<java.version>21</java.version>`. |
| **Maven** | 3.9.x (o `mvn` del sistema). |
| **Docker Desktop** | **Solo para las pruebas de integración (`*IT`)**: usan Testcontainers y levantan `postgis/postgis:17-3.5`. Debe estar **corriendo** antes de lanzar Maven. Las unitarias no lo necesitan. |

### Fijar JAVA_HOME

- **Git Bash:**
  ```bash
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
  ```
- **PowerShell:**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
  ```

> El VS Code Java Language Server también debe usar JDK 21 (`.vscode/settings.json`,
> no versionado). Si marca `getFirst()` / `getLast()` como error, está usando JDK 17.

---

## 2. Comandos

Todos desde la raíz del repo (`api-buses-jalapa/`).

### Toda la suite
```bash
mvn test
```
Corre unitarias + integración + escenarios Cucumber. Requiere Docker.

### Solo las unitarias del resumen (NO requiere Docker)
```bash
mvn test -Dtest=ResumenRutaServiceTest
```

### Solo la integración del resumen (requiere Docker)
```bash
mvn test -Dtest=ResumenRutaIT
```

### Unitarias + integración del resumen juntas
```bash
mvn test -Dtest=ResumenRutaServiceTest,ResumenRutaIT
```

### Escenarios Cucumber de la composición del resumen
```bash
mvn test -Dtest=PruebasDeAceptacionTest -Dcucumber.filter.tags="@SCRUM-282 or @SCRUM-283"
```

### Un solo método de prueba
```bash
mvn test -Dtest=ResumenRutaServiceTest#resumir_entrega_la_respuesta_aunque_el_bus_no_haya_reportado_posicion
```

### Compilar las pruebas sin ejecutarlas
```bash
mvn test-compile
```

---

## 3. Dónde quedan los reportes

| Archivo | Contenido |
|---|---|
| `target/surefire-reports/*.txt` y `*.xml` | Resultado por clase de prueba (JUnit). |
| `target/cucumber/reporte.html` | Reporte navegable de los escenarios Gherkin. |
| `target/cucumber/cucumber.xml` | JUnit XML de Cucumber (lo publica GitLab en el MR). |
| `src/test/resultados-pruebas.txt` | Resultado de la última corrida completa registrada a mano. |

---

## 4. Qué valida cada prueba del resumen

### `ResumenRutaServiceTest` — unitaria, mocks de los 3 colaboradores
`CatalogoService`, `TelemetriaService` y `DemandaService` mockeados.

- **compone_ruta_posicion_y_reservas** — orquesta las tres fuentes y rellena con 0 las paradas sin reservas.
- **tolera_que_el_bus_no_tenga_posicion** — sin posición, `posicionActual` queda null y la respuesta se entrega igual.
- **completa_con_cero_las_paradas_sin_reservas** — toda parada aparece, con 0 si no tiene reservas.
- **propaga_recurso_no_encontrado_si_la_ruta_no_existe** — 404 se propaga y no se consulta demanda ni telemetría.
- **consulta_demanda_una_sola_vez_para_todas_las_paradas** — un solo llamado agrupado (evita N+1).
- **consulta_telemetria_una_sola_vez** — una sola lectura de posición.
- **resumir_mapea_la_ruta_con_sus_paradas_en_orden** — mapeo a `RutaResumenDto` con paradas ordenadas por secuencia.
- **resumir_incluye_la_posicion_cuando_el_bus_ya_reporto** — `PosicionActualDto` con lat, lon, velocidad y `capturadoEn`.
- **resumir_entrega_la_respuesta_aunque_el_bus_no_haya_reportado_posicion** — caso sin posición: `posicionActual` null, respuesta completa.
- **resumir_lista_todas_las_paradas_con_su_conteo_incluidos_los_ceros** — `ReservasActivasDto.porParada` con una fila por parada y `calculadoEn` presente.
- **resumir_propaga_recurso_no_encontrado_si_la_ruta_no_existe** — 404 se propaga desde `resumir()`.

### `ResumenRutaIT` — integración MockMvc + PostGIS real
- **devuelve_200_con_ruta_posicion_y_reservas_en_una_sola_respuesta** — 200 con las tres partes en una sola llamada.
- **devuelve_200_con_la_posicion_vacia_cuando_el_bus_aun_no_ha_reportado** — 200 con `posicionActual: null`.
- **cada_parada_aparece_con_conteo_cero_cuando_no_tiene_reservas** — 8 paradas, todas con conteo 0.
- **el_conteo_por_parada_solo_suma_reservas_activa_y_renovada** — ACTIVA + RENOVADA cuentan; ABORDO, CANCELADA, EXPIRADA no.
- **los_datos_de_la_ruta_coinciden_con_el_endpoint_individual** — `ruta` del resumen == `GET /api/v1/rutas/1` (id, nombre, paradas, trazado).
- **la_posicion_coincide_con_el_endpoint_individual_de_telemetria** — `posicionActual` == `GET /api/v1/telemetria/posicion` (`capturadoEn` == `timestamp`).
- **una_ruta_que_no_existe_responde_404_con_el_formato_ApiError** — 404 con cuerpo `ApiError`.
- **un_rutaId_con_formato_invalido_responde_400_con_el_formato_ApiError** — `/rutas/abc/resumen` → 400 con cuerpo `ApiError`.

### Escenarios Cucumber — `componer_resumen_de_ruta.feature`
1. La ruta se obtiene con sus paradas en el orden del recorrido.
2. El resumen se puede componer aunque el bus aún no tenga posición.
3. Solo las reservas vigentes se cuentan por parada.
4. Una parada sin reservas aparece con conteo cero.
5. No se puede componer el resumen de una ruta inexistente.

---

## 5. Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop apagado. Arráncalo y reintenta. |
| `invalid target release: 21` / errores de sintaxis Java 21 | `JAVA_HOME` apunta a JDK 17. Ver sección 1. |
| Las `*IT` no se ejecutan | Se corren con `mvn test` (surefire), no hace falta `mvn verify`. Revisa el nombre: debe terminar en `IT` o `Test`. |
| Primera corrida muy lenta | Testcontainers descarga la imagen `postgis/postgis:17-3.5` una vez. |
