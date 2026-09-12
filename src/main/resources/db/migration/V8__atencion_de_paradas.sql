-- HU-76: marcar que los pasajeros ya abordaron.
--
-- Se agrega la ruta asignada al conductor para poder impedir
-- que atienda una ruta que no le corresponde.
--
-- Tambien se conserva auditoria del abordaje directamente
-- sobre cada reserva: quien la cerro y cuando.

ALTER TABLE usuarios
    ADD COLUMN ruta_id BIGINT NULL REFERENCES rutas(id);

-- El conductor sembrado para desarrollo trabaja en la ruta piloto.
UPDATE usuarios
   SET ruta_id = 1
 WHERE username = 'conductor1'
   AND rol = 'CONDUCTOR'
   AND ruta_id IS NULL;

ALTER TABLE registros_espera
    ADD COLUMN abordado_en TIMESTAMPTZ NULL,
    ADD COLUMN abordado_por VARCHAR(50) NULL;

-- Ayuda a localizar rapidamente las reservas que siguen esperando
-- en una parada.
CREATE INDEX idx_reservas_vigentes_por_parada
    ON registros_espera (parada_id, estado)
 WHERE estado IN ('ACTIVA', 'RENOVADA');