# Prompt de prueba visual — Fase 1: mapa legible y responsive

> Copiá todo lo que sigue y pegalo en un agente con navegador (Claude in Chrome o similar).

---

Sos tester de QA de EcoRuta (app web del bus municipal de Jalapa). Vas a verificar visualmente
las correcciones de la **Fase 1** hechas en la rama `qa-correcciones-develop` del frontend
(commit `fix(qa): mapa legible y sin cortes en pantallas pequenas`).

**URL a probar:** `<URL del entorno>` (local: `http://localhost:5173/`; QA: `https://qa.mibusjalapa.lat/`).

## Qué observó QA antes (Ecoruta_DESARROLLO.pdf)

- "El mapa se visualiza pero no es muy legible."
- "En pantallas pequeñas se presentan discrepancias."
- "Migración de Google Maps a OpenStreetMap: no cumple". Decisión del equipo: se mantiene
  MapLibre con teselas raster de OpenStreetMap y se corrige la legibilidad; el proveedor de
  teselas queda configurable con `VITE_MAPA_TESELAS`.

## Pasos

Para cada tamaño de ventana, recargá la página, esperá 8 s a que carguen las teselas y sacá
captura:

| # | Tamaño | Dispositivo que simula |
|---|---|---|
| 1 | 320 × 568 | iPhone SE (1ª gen.) |
| 2 | 360 × 740 | Android gama baja |
| 3 | 390 × 844 | iPhone 12–14 |
| 4 | 768 × 1024 | Tableta |
| 5 | 1280 × 800 | Escritorio |

En cada captura comprobá:

1. **Cabecera completa:** se ven el logo, el selector amarillo de ruta y el botón **"Acceder"**
   entero (con su flecha). Nada se corta por la derecha. En ≤ 400 px la palabra "EcoRuta" se
   oculta y queda solo el logo: es intencional.
2. **Sin desplazamiento horizontal:** en la consola, `document.documentElement.scrollWidth - innerWidth`
   debe dar `0`.
3. **La hoja inferior entra entera:** el botón verde "Usar la parada más cercana" se ve
   completo, sin tener que hacer scroll de la página.
4. **Mapa legible:** calles y nombres de calle con buen contraste (sin el tono lavado/sepia de
   antes). La atribución "© OpenStreetMap" se ve siempre.
5. **La ruta se ve entera:** el trazo verde de la ruta queda entre los avisos de arriba y la
   hoja de abajo, no tapado por ellos.
6. **Paradas sin amontonarse:** con el mapa alejado (zoom de ciudad), las paradas son círculos
   pequeños sin el número; al acercar con la rueda o con pellizco (zoom ≥ 14.5) aparece el
   contador de personas esperando debajo de cada parada.
7. **Grosor de la ruta:** alejado, el trazo es fino; al acercar se engrosa.
8. **Pantallas bajas (≤ 740 px de alto o ≤ 380 px de ancho):** no aparece la pista
   "Tocá en el mapa la parada donde vas a esperar" arriba (la hoja ya dice lo mismo) y los
   avisos de arriba son más compactos.
9. **Escritorio y tableta (≥ 768 px):** la hoja de reserva es una tarjeta de ~460 px abajo a la
   izquierda, con sombra; la atribución pasa a la esquina inferior derecha.
10. **Tocar una parada:** el mapa se centra en ella y se acerca lo necesario para leer las
    calles; la parada queda visible por encima de la hoja (no detrás).
11. **Botón "Ver el bus"** (redondo, icono de bus): centra el bus por encima de la hoja.
12. **Modo oscuro** (emulá `prefers-color-scheme: dark` en DevTools → Rendering): el mapa se
    oscurece sin invertir colores y las calles siguen leyéndose.

## Resultado esperado

Entregá una tabla con cada punto (1–12) × cada tamaño, marcado ✅ / ❌, y las capturas de los
❌ con una línea explicando qué se ve mal.
