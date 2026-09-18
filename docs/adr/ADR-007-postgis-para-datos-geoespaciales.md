# ADR-007: PostgreSQL con PostGIS para los datos geoespaciales

**Estado:** Aceptada — Julio 2026

## Contexto
El producto es geoespacial de punta a punta: paradas con coordenadas, trazado de la ruta,
histórico de posiciones del bus y —lo más delicado— la **geocerca de 150 m** que decide si un
registro de pasajero es válido. Esa regla es el principal control anti-abuso del contador
(ver [ADR-002](ADR-002-contador-anti-abuso.md)): si se puede burlar, el bus sale con un conteo
inflado y el producto pierde sentido.

Calcular distancias sobre la superficie terrestre no es restar coordenadas. A la latitud de Jalapa
(~14.63° N) un grado de longitud mide bastante menos que uno de latitud; una comparación ingenua
da errores de cientos de metros, del mismo orden que la geocerca que se quiere validar.

## Decisión
PostgreSQL 17 con la extensión PostGIS 3.5, y `hibernate-spatial` del lado de Java.

La geocerca se resuelve con `ST_DWithin` sobre el tipo `geography`, que calcula distancia real
sobre el elipsoide y devuelve metros:

```sql
ST_DWithin(p.ubicacion::geography,
           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
           :metros)
```

Todas las geometrías usan **SRID 4326** (WGS-84, el mismo que reporta el GPS y el que
esperan MapLibre y OpenStreetMap).

El esquema lo gobierna Flyway; Hibernate queda en `ddl-auto: validate`
(ver [HU-18](../../qa/hu-18-esquema-flyway/MANIFIESTO.md)).

## Consecuencias
+ La regla de negocio más importante se valida en la base con una función probada, no con
  aritmética casera propensa a errores.
+ Índices espaciales GiST: buscar la parada más cercana sigue siendo barato aunque crezca el histórico.
+ Una sola base para todo. No hay que sincronizar un almacén geoespacial aparte.
+ Deja listo el cálculo de ETA: interpolar sobre el trazado de la ruta ya es posible con las
  funciones que trae PostGIS.
- **No se puede usar H2 en las pruebas**: no soporta PostGIS. Las pruebas de integración corren
  con Testcontainers sobre la imagen `postgis/postgis:17-3.5`, así que **Docker es obligatorio
  para correr la suite**.
- Orden de coordenadas: PostGIS y JTS trabajan en `(lon, lat)`, mientras que los DTO exponen
  `latitud` y `longitud` con nombre. Invertirlas es el error clásico del dominio y hay que
  vigilarlo en cada revisión.

## Alternativas descartadas
**PostgreSQL sin PostGIS, calculando en Java**: implicaba escribir Haversine a mano y mantenerlo.
Es exactamente el tipo de código donde un error pasa desapercibido hasta que alguien registra
pasajeros desde su casa.

**MongoDB con índices geoespaciales**: sabe hacer consultas por proximidad, pero el resto del
modelo es claramente relacional y con reglas de integridad fuertes —como el índice único parcial
que garantiza un registro activo por dispositivo— que en un documental hay que emular a mano.

**MySQL con tipos espaciales**: soporta geometría, pero su cobertura de funciones es bastante
menor que la de PostGIS y su manejo de cálculos sobre `geography` es más limitado.
