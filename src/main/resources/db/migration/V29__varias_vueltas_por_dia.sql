-- El conductor recorre la ruta varias veces al dia. Antes cada parada se podia
-- cerrar una sola vez por dia; ahora una vez por vuelta.
--
-- Lo cerrado antes de esta migracion queda en la vuelta 1.

ALTER TABLE paradas_atendidas
    ADD COLUMN IF NOT EXISTS vuelta INT NOT NULL DEFAULT 1;

ALTER TABLE paradas_atendidas
    ADD CONSTRAINT ck_paradas_atendidas_vuelta CHECK (vuelta >= 1);

DROP INDEX IF EXISTS uq_parada_atendida_por_servicio;

CREATE UNIQUE INDEX IF NOT EXISTS uq_parada_atendida_por_vuelta
    ON paradas_atendidas (ruta_id, parada_id, fecha_servicio, vuelta);
