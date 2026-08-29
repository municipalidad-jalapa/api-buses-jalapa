-- Ruta de EJEMPLO para desarrollo y demo (SCRUM-304, HU-66).
--
-- ESTO NO ES LA RUTA REAL DEL BUS DE JALAPA. Es un circuito inventado para
-- poder ver el mapa funcionando antes de tener el dato bueno. Las paradas
-- oficiales y el trazado real los fija HU-41 (SCRUM-136), que esta en Sprint 5
-- y necesita levantamiento en campo. Esta migracion NO la sustituye ni la
-- adelanta: solo mejora la semilla mientras tanto.
--
-- Lo unico real aqui son las CALLES: los vertices del trazado salen de
-- OpenStreetMap --el mismo dato que pintan los tiles del mapa-- enrutados sobre
-- la red vial. Por eso la linea cae encima de las calles. Pero el recorrido que
-- describen se eligio para la demo, no porque el bus pase por ahi.
--
-- El circuito: Parque Central -> 6a Avenida -> 1a Calle (hacia el oeste)
-- -> giro en Llano Grande -> Calle Transito Rojas (de vuelta) -> Parque Central.
--
-- La semilla de V2 eran cuatro puntos en diagonal: sobre el mapa se veia una
-- recta cruzando manzanas, que es justo lo que un mapa de rutas no puede
-- mostrar. Esto la sustituye.
--
-- OJO: esta migracion se aplica en TODOS los entornos, incluidos QA y
-- produccion. flyway.enabled es true y el proyecto no tiene perfiles de Spring,
-- asi que no hay nada que la limite a desarrollo. Se asume a conciencia: los
-- datos que hay hoy en produccion tambien son inventados (V2), y estos al menos
-- caen sobre calles de verdad. El nombre de la ruta lo dice en voz alta para
-- que nadie los tome por buenos.
--
-- No se borran las paradas 1-4: registros_espera.parada_id las referencia y es
-- NOT NULL. Se actualizan en sitio y se anaden las cuatro nuevas.

UPDATE rutas
   SET nombre  = 'Ruta de ejemplo - Centro de Jalapa',
       trazado = ST_SetSRID(ST_GeomFromText('LINESTRING(
  -89.981202 14.634878,
  -89.981241 14.634983,
  -89.981750 14.634783,
  -89.982242 14.634592,
  -89.982770 14.634360,
  -89.983513 14.634051,
  -89.984031 14.633843,
  -89.984827 14.633509,
  -89.985636 14.633161,
  -89.986725 14.632733,
  -89.987237 14.632479,
  -89.987433 14.632399,
  -89.987591 14.632338,
  -89.987747 14.632275,
  -89.988584 14.631911,
  -89.988724 14.631855,
  -89.989338 14.631612,
  -89.989399 14.631595,
  -89.989959 14.631420,
  -89.990434 14.631244,
  -89.991110 14.631070,
  -89.991385 14.630986,
  -89.992081 14.630762,
  -89.993094 14.630485,
  -89.993971 14.630240,
  -89.995119 14.629958,
  -89.996067 14.629695,
  -89.997124 14.629418,
  -89.998152 14.629148,
  -89.999062 14.628897,
  -89.999829 14.628685,
  -90.000282 14.628593,
  -90.000748 14.628470,
  -90.001169 14.628366,
  -90.001669 14.628237,
  -90.002797 14.627954,
  -90.003132 14.627843,
  -90.003268 14.627755,
  -90.003423 14.627655,
  -90.002758 14.627784,
  -90.001622 14.628014,
  -90.000699 14.628182,
  -89.999765 14.628360,
  -89.999740 14.628359,
  -89.998985 14.628507,
  -89.998023 14.628657,
  -89.997042 14.628848,
  -89.995950 14.629087,
  -89.994964 14.629280,
  -89.993836 14.629479,
  -89.992811 14.629681,
  -89.991896 14.629862,
  -89.990842 14.630061,
  -89.990291 14.630158,
  -89.989531 14.630317,
  -89.988312 14.630611,
  -89.987819 14.630827,
  -89.987478 14.631010,
  -89.986912 14.631279,
  -89.986235 14.631600,
  -89.985235 14.632090,
  -89.985048 14.632170,
  -89.984449 14.632427,
  -89.983691 14.632814,
  -89.983389 14.632900,
  -89.983136 14.632995,
  -89.982912 14.633078,
  -89.982726 14.633174,
  -89.982361 14.633363,
  -89.981895 14.633630,
  -89.981420 14.633921,
  -89.980955 14.634208,
  -89.980961 14.634224,
  -89.981202 14.634878
 )'), 4326)
 WHERE id = 1;

UPDATE paradas SET nombre = 'Parque Central',
       ubicacion = ST_SetSRID(ST_MakePoint(-89.981202, 14.634878), 4326)
 WHERE id = 1;
UPDATE paradas SET nombre = '1a Calle - Mercado',
       ubicacion = ST_SetSRID(ST_MakePoint(-89.987308, 14.632450), 4326)
 WHERE id = 2;
UPDATE paradas SET nombre = '1a Calle - El Calvario',
       ubicacion = ST_SetSRID(ST_MakePoint(-89.993654, 14.630328), 4326)
 WHERE id = 3;
UPDATE paradas SET nombre = '1a Calle - Terminal',
       ubicacion = ST_SetSRID(ST_MakePoint(-90.000143, 14.628621), 4326)
 WHERE id = 4;

INSERT INTO paradas (nombre, ubicacion, orden, ruta_id) VALUES
 ('Llano Grande', ST_SetSRID(ST_MakePoint(-90.003034, 14.627730), 4326), 5, 1),
 ('Transito Rojas - Hospital', ST_SetSRID(ST_MakePoint(-89.996436, 14.628981), 4326), 6, 1),
 ('Transito Rojas - Instituto', ST_SetSRID(ST_MakePoint(-89.989841, 14.630252), 4326), 7, 1),
 ('Transito Rojas - Chipilapa', ST_SetSRID(ST_MakePoint(-89.984545, 14.632386), 4326), 8, 1);
