-- Desarrollo-135 (HU): vigencia de cinco minutos con renovacion y expiracion.
--
-- La tabla registros_espera ya existe desde V1 con estado 'ACTIVO' y el indice
-- unico parcial "un registro activo por dispositivo". Esta HU le da a la reserva
-- un ciclo de vida de cinco estados (ACTIVA, RENOVADA, ABORDO, CANCELADA,
-- EXPIRADA) y hace que una reserva que ya no esta vigente deje de ocupar el
-- cupo del dispositivo.

-- 1. El valor por defecto pasa a 'ACTIVA' (femenino, "la reserva") y se migran
--    las filas que hubiera con el valor viejo. En las pruebas la tabla se
--    trunca y en produccion esta vacia, pero el UPDATE se deja por seguridad.
ALTER TABLE registros_espera ALTER COLUMN estado SET DEFAULT 'ACTIVA';
UPDATE registros_espera SET estado = 'ACTIVO'  WHERE estado IS NULL;
UPDATE registros_espera SET estado = 'ACTIVA'  WHERE estado = 'ACTIVO';

-- 2. El indice unico parcial "una reserva vigente por dispositivo" pasa a
--    considerar SOLO los estados vigentes. Asi una reserva EXPIRADA (o
--    CANCELADA) ya no bloquea una reserva nueva del mismo dispositivo: es la
--    garantia dura en BD del criterio de aceptacion.
DROP INDEX IF EXISTS uq_registro_activo_por_dispositivo;
CREATE UNIQUE INDEX uq_reserva_vigente_por_dispositivo
    ON registros_espera (dispositivo_id)
    WHERE estado IN ('ACTIVA', 'RENOVADA', 'ABORDO');

-- 3. idx_registro_activo (estado, parada_id) de V1 se conserva. La tarea
--    programada barre por vencimiento, asi que conviene un indice por expira_en
--    de las reservas que todavia pueden expirar.
CREATE INDEX idx_reserva_por_vencer
    ON registros_espera (expira_en)
    WHERE estado IN ('ACTIVA', 'RENOVADA');
