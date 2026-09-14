-- HU-72: parametros de tiempo estimado por ruta.
--
-- En develop V7-V12 ya existen (reservas, identidad, avisos, segunda ruta).
-- Esta migracion es V13 para no chocar con esas.
--
-- Horarios oficiales del Bus Electrico Municipal de Jalapa:
--   * Entre semana: 06:20 y 10:15
--   * Jueves: 09:00
--   * Domingo: jornada continua 09:00-21:00 (ciclo ~44 min + 15 min en parque)
-- Duracion 44 min (44 min 8 s Parque-Porvenir-Parque), permanencia 15 min.

CREATE TABLE parametros_de_ruta (
    ruta_id                        BIGINT PRIMARY KEY REFERENCES rutas(id),
    duracion_recorrido_minutos     INTEGER NOT NULL,
    detencion_por_parada_segundos  INTEGER NOT NULL,
    permanencia_origen_minutos     INTEGER NOT NULL,
    horarios_habil                 TEXT NOT NULL,
    horarios_jueves                TEXT NOT NULL,
    horarios_domingo               TEXT NOT NULL
);

INSERT INTO parametros_de_ruta (
    ruta_id,
    duracion_recorrido_minutos,
    detencion_por_parada_segundos,
    permanencia_origen_minutos,
    horarios_habil,
    horarios_jueves,
    horarios_domingo
)
SELECT r.id,
       44,
       18,
       15,
       '06:20,10:15',
       '09:00',
       '09:00,10:00,11:00,12:00,13:00,14:00,15:00,16:00,17:00,18:00,19:00,20:00,21:00'
  FROM rutas r
 WHERE r.nombre = 'Ruta de ejemplo - Centro de Jalapa';
