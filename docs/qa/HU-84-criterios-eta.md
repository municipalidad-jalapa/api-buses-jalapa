# HU-84: Criterios de aceptación para el ETA

**Estado:** Propuesta — pendiente de validar el margen de error con la Municipalidad.

## Historia

Como líder de QA quiero criterios objetivos para aprobar o rechazar el ETA, para que "el tiempo
estimado está bien" deje de ser una opinión.

Este documento cubre los tres criterios de aceptación de la HU: el margen de error, los casos que
tiene que pasar el ETA, y cómo se verifica en producción contra lo que realmente pasó.

**Importante:** este documento define *cómo se prueba* el ETA. No implementa el cálculo del ETA en
sí — hoy el backend solo expone la posición vigente del bus (`GET /telemetria/posicion`, ADR-008);
todavía no existe un endpoint ni un servicio que calcule un tiempo estimado de llegada. Los tres
puntos de abajo son la vara con la que se mide esa funcionalidad el día que se construya.

## 1. Margen de error aceptable

**Definición de error:** dado un ETA emitido para que un bus llegue a una parada,

```
error = | hora_real_de_llegada − (hora_de_emisión_del_eta + eta_predicho) |
```

Un margen único en minutos no es justo en los dos extremos: 1 minuto de error es grave si el ETA
decía "llega en 2 minutos" (el pasajero corrió o se quedó por gusto), pero es ruido si el ETA decía
"llega en 25 minutos". Por eso la propuesta usa **bandas según qué tan lejos está el bus**, no un
número fijo.

| Horizonte del ETA emitido | Margen de error propuesto      | Por qué                                                                 |
|----------------------------|--------------------------------|--------------------------------------------------------------------------|
| ≤ 3 minutos                 | ± 1 minuto                     | Es la ventana donde el pasajero decide si corre o no; el error más caro |
| 3–10 minutos                | ± 2 minutos ó 20% (lo mayor)   | Todavía importa, pero el tráfico ya mete incertidumbre real            |
| > 10 minutos                 | ± 20%, con tope de 6 minutos   | A esta distancia al pasajero le alcanza con el orden de magnitud       |
| Última posición fuera del TTL de vigencia | No aplica margen — no debe mostrarse como ETA numérico | Un cálculo sobre una posición vieja no es una predicción, es una suposición (ver caso C en la sección 2) |

Estos números son un **punto de partida para la negociación con la Municipalidad**, no un acuerdo
cerrado. Quedan pendientes:

- [ ] Reunión con la Municipalidad para revisar y firmar las bandas de la tabla (o las que se
      acuerden en su lugar).
- [ ] Registrar aquí la fecha del acuerdo y quién lo firmó por parte de la Municipalidad.
- [ ] Decidir si el margen se acuerda igual para todas las rutas o si alguna ruta (p. ej. con tramo
      de terracería o cruce fronterizo con más variabilidad) necesita una banda distinta.

## 2. Casos de prueba

Los casos se agrupan en las tres situaciones que pide la HU. `CAT` referencia la banda de la tabla
anterior.

| ID | Escenario | Precondición | Resultado esperado |
|----|-----------|--------------|---------------------|
| CP-ETA-01 | Bus en ruta, velocidad normal | El bus se mueve dentro del rango de velocidad esperado para ese tramo | El error del ETA cae dentro de la banda de la tabla 1 según su horizonte |
| CP-ETA-02 | Bus en ruta, tráfico más lento de lo normal | El bus se mueve, pero por debajo de la velocidad esperada del tramo | El ETA se ajusta al alza; no se queda anclado a la velocidad de crucero de la ruta |
| CP-ETA-03 | Bus detenido en una parada oficial | Posición dentro de la geocerca de una parada, velocidad ≈ 0 | El ETA hacia la *siguiente* parada sí suma el tiempo de espera típico en parada, no solo el tiempo de viaje |
| CP-ETA-04 | Bus detenido fuera de una parada (semáforo, tráfico) | Velocidad ≈ 0 fuera de cualquier geocerca de parada, por menos de 2 minutos | El ETA no colapsa a "nunca llega"; trata la parada breve como parte normal del tramo |
| CP-ETA-05 | Bus detenido fuera de ruta por tiempo prolongado (avería, fin de turno) | Velocidad ≈ 0 fuera de geocerca por más de un umbral acordado (p. ej. 5 minutos) | El sistema dejar de proyectar un ETA optimista y lo marca como degradado o "sin estimación confiable" |
| CP-ETA-06 | Última posición reciente pero con leve rezago | Antigüedad de la posición menor al TTL de vigencia (hoy 20 min para la posición vigente, ver `application.yml`) | Se muestra un ETA, idealmente con indicación de qué tan reciente es el dato en que se basa |
| CP-ETA-07 | Ausencia de datos recientes | Antigüedad de la posición mayor al TTL de vigencia | El sistema **no** muestra un número de minutos; muestra explícitamente "sin datos recientes" o equivalente |
| CP-ETA-08 | El equipo a bordo nunca reportó en el viaje actual | No hay ninguna posición histórica para ese vehículo en la ventana vigente | Mismo comportamiento que CP-ETA-07: sin ETA numérico, no un valor por defecto engañoso (p. ej. 0 o el ETA del viaje anterior) |
| CP-ETA-09 | Cambio de equipo a bordo a mitad de ruta | Se reemplaza el equipo del vehículo (ver `CambioDeEquipoConservaHistorialIT`) mientras el bus está en tránsito | El ETA no se rompe ni reinicia solo porque cambió el equipo; sigue basado en la posición real del vehículo |
| CP-ETA-10 | Bus fuera de la ruta/geocerca esperada (desvío) | Posición reportada se aleja del trazado conocido de la ruta | El sistema no fuerza un ETA calculado sobre un trazado que el bus ya no sigue; se marca como no confiable en vez de dar un número con apariencia de exacto |

Cada fila de esta tabla es candidata a convertirse en un escenario ejecutable de Cucumber el día
que exista el servicio de ETA, siguiendo la convención del proyecto (`src/test/resources/features`,
ver README § "Pruebas de aceptación en Gherkin"). Para no dejar pasos sin definir rompiendo
`mvn test` mientras tanto, se deja el borrador listo para copiar en ese momento:

```gherkin
# language: es
@HU-84
Característica: Evaluar la calidad del ETA contra criterios objetivos

  Como líder de QA
  quiero criterios objetivos para aprobar o rechazar el ETA
  para que "el tiempo estimado está bien" deje de ser una opinión

  @criterio-1
  Escenario: El error del ETA para una llegada cercana está dentro del margen acordado
    Dado un bus a menos de tres minutos de una parada
    Cuando el sistema calcula el ETA hacia esa parada
    Y el bus llega realmente a la parada
    Entonces la diferencia entre el ETA predicho y la llegada real es de máximo un minuto

  @criterio-2
  Escenario: ETA con el bus detenido en una parada
    Dado un bus detenido dentro de la geocerca de una parada
    Cuando el sistema calcula el ETA hacia la siguiente parada
    Entonces el ETA suma el tiempo de espera en parada al tiempo de viaje restante

  @criterio-2
  Escenario: ETA con el bus detenido fuera de una parada
    Dado un bus detenido fuera de cualquier geocerca de parada por más de cinco minutos
    Cuando el sistema calcula el ETA hacia la siguiente parada
    Entonces el sistema marca el ETA como no confiable en vez de asumir la velocidad de crucero

  @criterio-2
  Escenario: ETA con el bus en ruta a velocidad normal
    Dado un bus en movimiento dentro del rango de velocidad esperado para su tramo
    Cuando el sistema calcula el ETA hacia la siguiente parada
    Entonces el error del ETA está dentro del margen acordado para ese horizonte

  @criterio-2
  Escenario: Ausencia de datos recientes
    Dado un bus cuya última posición es más antigua que el TTL de vigencia
    Cuando el sistema calcula el ETA
    Entonces el sistema no muestra un tiempo estimado sino un aviso de datos no recientes

  @criterio-3
  Escenario: El registro de predicho contra real permite calcular el error de una predicción
    Dado un ETA emitido para un bus hacia una parada
    Cuando el bus llega realmente a esa parada
    Entonces el sistema registra el ETA predicho, la hora real de llegada y el error resultante
```

## 3. Método de verificación: predicho contra real

Sin un registro que capture ambos valores, "el ETA está bien" sigue siendo una opinión aunque ya
tengamos bandas de margen — hay que poder auditarlo con datos.

### 3.1 Qué hay que registrar

Por cada ETA emitido, guardar:

- Identificador del vehículo y de la parada objetivo.
- Momento en que se emitió el ETA y el valor predicho (segundos o minutos).
- Antigüedad de la posición usada para el cálculo (distingue los casos CP-ETA-01…10 al analizar
  resultados agrupados).
- Estado del bus al momento del cálculo: en ruta / detenido en parada / detenido fuera de parada /
  sin datos recientes.

Y al cerrar la predicción:

- Momento de la **llegada real**, definida como el instante en que una posición reportada entra a
  la geocerca de la parada objetivo (la misma noción de geocerca que ya maneja el proyecto, ver
  `geocerca-metros` en `application.yml`).
- El error resultante, calculado como en la sección 1.

Esto implica una tabla nueva (algo como `registro_eta`, análoga a `PosicionHistorica` pero para
predicciones) que debe nacer junto con la funcionalidad de ETA — no es parte de esta HU de QA, pero
esta HU sí exige que exista antes de poder cerrarse, porque sin ella el criterio 3 no es verificable.

### 3.2 Cómo se calcula si el ETA "pasó"

1. Agrupar los registros cerrados por banda de horizonte (tabla de la sección 1) **y por categoría**
   (en ruta / detenido en parada / detenido fuera de parada / con datos rezagados) — mezclar todo
   en un solo promedio esconde justo el peor caso, que es el que le importa a QA.
2. Por grupo, calcular:
   - Error absoluto medio (MAE) y mediana.
   - % de predicciones dentro del margen acordado para esa banda.
   - Sesgo: si el sistema tiende a adelantarse o a atrasarse sistemáticamente (no solo el error
     absoluto, sino el error con signo).
3. Definir un umbral de aprobación, por ejemplo: **≥ 85% de las predicciones dentro del margen**,
   sobre un piloto mínimo de N días o M predicciones por ruta (el número concreto también se
   acuerda con la Municipalidad al mismo tiempo que el margen del punto 1).
4. El caso "sin datos recientes" (CP-ETA-07/08) no entra a este cálculo de error — ahí lo que se
   audita es que el sistema **no haya mostrado un número** cuando no correspondía, no la precisión
   de un número que no debió existir.

### 3.3 Cadencia

- Durante el piloto: revisión semanal de las métricas por parte de QA, con corte a la Municipalidad
  antes de decidir si el ETA pasa a producción.
- En producción: revisión mensual, o inmediata si algún grupo cae por debajo del umbral acordado.

## 4. Pendientes para cerrar esta HU

- [ ] Acuerdo firmado con la Municipalidad sobre las bandas de margen (sección 1) y el umbral de
      aprobación (sección 3.2).
- [ ] Definir con el equipo de desarrollo el esquema de `registro_eta` antes de implementar el
      cálculo del ETA, para que el registro predicho-vs-real exista desde el primer despliegue y no
      se agregue después "para poder medir".
- [ ] Mover los escenarios Gherkin de la sección 2 a `src/test/resources/features/eta.feature` con
      sus pasos implementados, el día que el servicio de ETA exista.
