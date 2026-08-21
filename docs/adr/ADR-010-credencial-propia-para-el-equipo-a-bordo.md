# ADR-010: Credencial propia para el equipo a bordo

**Estado:** Aceptada — Agosto 2026

## Contexto
Quien reporta la posición del bus es el equipo montado en la cabina, no el teléfono de nadie
(ADR-006). Hasta ahora la ingesta se había pensado protegida por el rol `CONDUCTOR`, es decir por
la sesión de una persona. Eso trae tres problemas concretos:

- Si el conductor cierra sesión, o su cuenta se desactiva, el bus deja de reportar aunque el
  aparato siga funcionando perfectamente.
- Si se roba una tableta, revocarla obliga a tocar la cuenta de una persona, que probablemente
  siga trabajando.
- No hay forma de saber qué aparato mandó qué: todas las lecturas llegan a nombre del mismo
  usuario.

Además, la identidad de personas es de otra historia (SCRUM-134) y va con **Firebase
Authentication**, que prohíbe explícitamente contraseñas propias y bcrypt. Colgar la credencial de
una máquina de ese módulo lo contradiría el primer día.

## Decisión
El equipo a bordo se autentica con una credencial propia, independiente de las cuentas de
personas: un token opaco de dos partes que viaja en la cabecera `Authorization`.

```
Authorization: Bearer eq_<codigoPublico>.<secreto>
```

- `codigoPublico`: `eq_` + 72 bits aleatorios en Base64URL. Indexado y **seguro de registrar**.
- `secreto`: 256 bits aleatorios, 43 caracteres. En la base solo vive su **bcrypt**; el valor en
  claro se muestra una sola vez, al emitirlo, y no se puede recuperar.

Es de dos partes porque bcrypt lleva sal: no se puede buscar la fila por el hash del secreto
presentado. El código público localiza la fila y el secreto se verifica contra su hash.

El prefijo `eq_` no es decoración. SCRUM-134 va a poner un JWT de Firebase en la **misma**
cabecera, y el prefijo permite que el filtro del equipo decida en O(1), sin ir a la base y sin
lanzar, si el bearer es suyo; lo que no calza se deja intacto para el otro filtro. Los dos
conviven sin coordinarse.

**Cada petición se valida contra la base, sin caché.** Es el precio deliberado de que revocar
surta efecto de inmediato.

Rotar una credencial es revocar la fila vieja e insertar una nueva, nunca reescribir el hash
—`secreto_hash` está mapeado `updatable = false` para que eso no se pueda hacer ni por descuido—.
Así la tabla `equipos` conserva el rastro de cada aparato que existió, y cada posición guarda el
`equipo_id` que la envió.

El vínculo entre un equipo y el vehículo en el que va montado lo añade **SCRUM-143 (HU-48)** sobre
esta decisión: un vehículo tiene un solo equipo activo, garantizado por un índice único parcial, y
las posiciones se atribuyen al **vehículo**, congelado en el INSERT. Como rotar es revocar y crear,
la tabla `equipos` es además el historial de qué aparato llevó cada bus, sin una segunda tabla.

## Consecuencias
+ La ingesta deja de depender de la sesión de una persona: el bus reporta aunque nadie haya
  iniciado sesión.
+ Se revoca un solo equipo sin tocar ninguna cuenta, y el corte es instantáneo.
+ Queda registro de qué aparato mandó cada lectura, útil para diagnosticar un GPS que deriva.
- **Un bcrypt por petición de ingesta**, unos 50-80 ms de CPU. Con un bus mandando un lote cada
  pocos segundos es invisible; es el costo aceptado a cambio de la revocación inmediata. Si
  alguna vez importara, la salida es una columna `secreto_hmac` con `HMAC-SHA256(pepper, secreto)`
  verificada en vez del bcrypt: mismo formato de token, cero cambios en el equipo. **No añadir una
  caché de credenciales**: sería exactamente lo que el criterio de revocación prohíbe.
- El secreto es irrecuperable. Perderlo obliga a revocar y emitir de nuevo. Es intencional.
- El token solo es confidencial sobre TLS. En producción lo termina el proxy inverso (ADR-009).
- Con SCRUM-143 encima, no hay ventana de gracia para cambiar de equipo: el índice único parcial
  impide dos activos en un mismo bus. Si en campo hiciera falta instalar y verificar la tableta
  nueva antes de quitar la vieja, ese índice tiene que cambiar.

## Alternativas descartadas
- **Reutilizar el JWT de personas.** Acopla la identidad de una máquina a la de un humano, que es
  el problema que se venía a resolver. Y un JWT no se puede revocar antes de que expire sin
  mantener una lista de revocados consultada en cada petición — el mismo costo, con más piezas.
- **mTLS con certificado por equipo.** Robusto, pero exige operar una PKI: emisión, renovación,
  CRL. Es el mismo argumento que ADR-009 usa contra Kubernetes — después del 31/10 esto lo opera
  la Municipalidad, y un `curl` con una cabecera es enseñable en una tarde.
- **Una API key compartida por toda la flota.** Una sola fuga obliga a reconfigurar todos los
  buses. Contradice de frente el "poder revocar un solo equipo".
- **SHA-256 sin sal para el secreto.** Permitiría buscar por hash y ahorrarse el token de dos
  partes, pero un volcado de la base se convierte en credenciales usables por fuerza bruta. El
  token de dos partes da la misma búsqueda indexada sin ese riesgo.
- **Sembrar un equipo en Flyway.** Cómodo para desarrollo, pero Flyway corre igual en producción:
  sería una credencial válida publicada en git. No se siembra ninguno.

## Notas
- Añade el módulo `flota` a los cinco que nombra ADR-005 (`catalogo`, `demanda`, `telemetria`,
  `identidad`, `notificaciones`). La tableta atornillada en una cabina es un activo, no una
  persona; `identidad/` queda reservado para Firebase.
- El acceso `ROLE_ADMIN` a los endpoints de alta y revocación es **provisional**: hoy lo concede
  un filtro por la cabecera `X-Admin-Token` porque SCRUM-134 no existe todavía. Para retirarlo:
  borrar el paquete `seguridad/bootstrap/`, el bloque marcado en `SecurityConfig`, la propiedad
  `ecoruta.admin` y la variable `ECORUTA_ADMIN_TOKEN`. **Los controladores no cambian.**
- ADR-001 a ADR-004 se citan en otros documentos pero no están en este repositorio. ADR-004 se
  mencionaba como base de la autenticación del dispositivo; si aparece y decide otra cosa, este
  ADR debe marcarse como que lo reemplaza.
