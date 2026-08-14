# EcoRuta — Backend

API del bus eléctrico municipal de Jalapa. Java 21 + Spring Boot 3.4, PostgreSQL 17 con PostGIS,
monolito modular (`catalogo`, `demanda`, `telemetria`, `identidad`, `notificaciones`).

## Levantar el entorno

```bash
docker compose up --build
```

Levanta PostGIS y el backend en `http://localhost:8080`. Flyway aplica las migraciones al arrancar.

Sin Docker (requiere PostGIS local):

```bash
mvn spring-boot:run
```

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Colección de Postman: `docs/postman/EcoRuta.postman_collection.json` (importar en Postman;
  la variable `baseUrl` apunta a `http://localhost:8080`).

## Endpoints principales

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/api/v1/rutas` | público |
| `POST` | `/api/v1/demanda/registros` | público (UUID de dispositivo) |
| `GET` | `/api/v1/demanda/estado` | público |
| `POST` | `/api/v1/auth/login` | público |
| `POST` | `/api/v1/telemetria/posiciones` | rol `CONDUCTOR` |
| `GET` | `/api/v1/telemetria/posicion` | público |
| `GET` | `/api/v1/telemetria/stream` | público (SSE) |

## Configuración

Por variables de entorno: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.
El `JWT_SECRET` del compose es solo de desarrollo.

Reglas de negocio configurables en `application.yml`: `ecoruta.demanda.umbral-salida` (10),
`ttl-minutos` (20), `geocerca-metros` (150).

## Pruebas

```bash
mvn test
```

Integración con Testcontainers e imagen `postgis/postgis:17-3.5` (requiere Docker en ejecución).
