# ADR-005: Spring Boot como plataforma del backend

**Estado:** Aceptada — Julio 2026

## Contexto
Hay que entregar un backend en producción, con usuarios reales, en 7.5 semanas de desarrollo
efectivo. El equipo son 7 estudiantes cuya formación principal es Java. La aplicación necesita
API REST, acceso a datos geoespaciales, autenticación y un canal de tiempo real, y la
Municipalidad tiene que poder mantenerla después de la entrega.

## Decisión
Java 21 con Spring Boot 3.4, construido con Maven.

Pesa más la familiaridad del equipo que cualquier ventaja técnica marginal de otra plataforma:
con el plazo que hay, aprender un ecosistema nuevo se paga con funcionalidad no entregada.
Spring Boot además trae de fábrica todo lo que el proyecto necesita —Spring Data JPA, Spring
Security, `SseEmitter` para el tiempo real, Actuator para salud— sin sumar dependencias de
terceros que después haya que sostener.

Java 21 por ser LTS: la Municipalidad recibe algo con soporte largo, no una versión que caduca.

## Consecuencias
+ El equipo produce desde el primer día; no hay curva de aprendizaje de plataforma.
+ Un solo artefacto (`jar`) y un solo pipeline. Ver [ADR-001](ADR-001-monolito-modular.md).
+ Contratar o reemplazar a alguien que sepa Java en Guatemala es realista.
- Consumo de memoria mayor que alternativas más livianas: hay que dimensionar el servidor
  con eso en cuenta.
- Arranque más lento que un binario nativo, lo que se nota en cada despliegue.

## Alternativas descartadas
**Node.js con Express o NestJS**: más liviano y con arranque instantáneo, pero el equipo no lo
domina y el manejo de datos geoespaciales con PostGIS es menos maduro que el de Hibernate Spatial.

**Python con Django o FastAPI**: excelente para geodatos con GeoDjango, pero implicaba que cinco
de los siete devs aprendieran un lenguaje nuevo durante el sprint.

**Quarkus o Micronaut**: arranque nativo y menor huella, ventaja real para contenedores. Se
descartó porque su ecosistema y su documentación son mucho más chicos que los de Spring, y ante
un problema el equipo se quedaría sin respuestas a mano.
