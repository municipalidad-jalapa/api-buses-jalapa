# ADR-009: Docker Compose en vez de Kubernetes

**Estado:** Aceptada — Agosto 2026

## Contexto
El sistema son tres piezas: un backend, una base PostgreSQL con PostGIS y una app web estática.
La carga esperada es la de **una ruta de bus en un municipio**: decenas de pasajeros concurrentes
en hora pico, no miles.

El plan inicial mencionaba desplegar en AKS (Kubernetes gestionado en Azure). Al revisarlo contra
el plazo y contra quién va a operar esto después de la entrega, no se sostiene.

## Decisión
Docker Compose, tanto en desarrollo como en producción, sobre un único servidor Linux con un proxy
inverso al frente que termina TLS.

El criterio es el costo de operación, no la elegancia. Kubernetes resuelve problemas que este
proyecto no tiene —escalado horizontal automático, despliegues sin corte, orquestación de decenas
de servicios— y a cambio pide que alguien lo entienda para diagnosticar una falla. Después del
31/10 ese alguien es el personal de la Municipalidad. Un `docker compose logs` es enseñable en una
tarde; depurar un pod en `CrashLoopBackOff` no.

## Consecuencias
+ El mismo `docker-compose.yml` describe el entorno local y el de producción: se acaba el
  "en mi máquina funciona".
+ Un dev nuevo levanta todo con `docker compose up`. Es lo que hace verificable el criterio de
  arranque de HU-17.
+ Traspaso realista a la Municipalidad: el manual de operación cabe en unas pocas páginas.
+ Sin costo de un clúster gestionado, que para tres contenedores sería desproporcionado.
- **No hay alta disponibilidad.** Si el servidor cae, el servicio cae. Se mitiga con reinicio
  automático de los contenedores, monitoreo de caída (HU-56) y respaldos de la base (HU-68).
- **El despliegue corta el servicio unos segundos** mientras el contenedor se reinicia. Aceptable
  para un servicio de transporte diurno, y se programa fuera del horario de operación del bus.
- Escalar horizontalmente exigiría rehacer esta decisión. Con una ruta y un bus, no está cerca de
  ser un problema.

## Alternativas descartadas
**AKS o cualquier Kubernetes gestionado**: era el plan original. Se descartó por el costo del
clúster, por las semanas que consume escribir y depurar manifiestos, y sobre todo porque deja a la
Municipalidad con algo que no puede operar sola.

**k3s o un Kubernetes liviano en un solo nodo**: baja el costo de infraestructura pero conserva
íntegra la complejidad conceptual, que es la parte cara.

**Despliegue directo sobre el sistema, sin contenedores**: menos piezas, pero obliga a instalar y
mantener a mano JDK, PostgreSQL y PostGIS en el servidor, y a que ese entorno coincida con el de
desarrollo. Es exactamente la clase de divergencia que los contenedores evitan.

## Nota
Esto reemplaza la mención a AKS que quedó en la documentación anterior del proyecto. Cualquier
documento que siga diciendo que producción va en AKS está desactualizado.
