-- HU-76: marcar que los pasajeros ya abordaron.
--
-- HU-57 ya agrega abordaje_en.
-- HU-76 agrega la identidad del conductor y
-- la asignacion del conductor a una ruta.

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS ruta_id BIGINT NULL REFERENCES rutas(id);

-- El conductor de desarrollo trabaja en la ruta piloto.
UPDATE usuarios
   SET ruta_id = 1
 WHERE username = 'conductor1'
   AND rol = 'CONDUCTOR'
   AND ruta_id IS NULL;

-- HU-57 ya almacena abordaje_en.
-- HU-76 solamente necesita guardar quien fue el conductor.
ALTER TABLE registros_espera
    ADD COLUMN IF NOT EXISTS abordado_por VARCHAR(50) NULL;

-- Facilita encontrar las reservas pendientes
-- de una parada.
CREATE INDEX IF NOT EXISTS idx_reservas_vigentes_por_parada
    ON registros_espera (parada_id, estado)
 WHERE estado IN ('ACTIVA', 'RENOVADA');