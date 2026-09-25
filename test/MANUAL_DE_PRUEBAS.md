# Manual de pruebas — HU Desarrollo-135 y Desarrollo-95

Este manual explica cómo ejecutar **todas** las pruebas del backend `ecoruta-backend`
para las funcionalidades de demanda, vigencia y expiración (HU-135) así como la
seguridad de la API y rate limiting (HU-95). Las pruebas incluyen automáticas 
(unitarias e integración) y las **manuales** end-to-end simuladas (con `curl`).

> Los archivos de prueba nuevos o modificados están copiados en
> [`casos/`](casos/) para lectura. **Los que Maven ejecuta de verdad viven en
> `src/test/java/`** (Maven exige esa ruta); no borres los originales.

Criterios de aceptación (Jira Desarrollo-135 y Desarrollo-95):
1. Al crearse, la reserva expira 5 min después y ese momento viaja en `expiraEn`.
2. Renovar una reserva vigente extiende +5 min y responde 200 con el nuevo `expiraEn`.
3. Un proceso programado marca `EXPIRADA` toda reserva vencida.
4. Límite de peticiones por dispositivo y por IP en los endpoints públicos.
5. Cabeceras de seguridad configuradas y CORS restringido a orígenes conocidos.
6. Un dispositivo no puede registrar demanda a un ritmo imposible para una persona.

---

## 1. Requisitos del entorno

| Requisito | Detalle |
|---|---|
| **JDK 21+** | El `pom.xml` fija `java.version = 21`. Un JDK 17 o menor **no compila**; hay que apuntar `JAVA_HOME` a un JDK 21 o más nuevo. |
| **Docker** | Las pruebas `*IT` levantan un contenedor `postgis/postgis:17-3.5` con Testcontainers. Docker Desktop tiene que estar **corriendo** (`docker info` no debe fallar). |
| **Maven** | 3.9+. Necesita acceso a internet la primera vez para bajar plugins. |
| **curl** | Para las pruebas manuales (incluido en Git Bash / Windows 10+). |

### Fijar el JDK

**Git Bash:**
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"