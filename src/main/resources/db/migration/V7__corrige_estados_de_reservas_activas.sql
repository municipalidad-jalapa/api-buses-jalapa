-- SCRUM-306: normaliza el estado vigente de registros_espera.
--
-- V1 sembraba el default e el indice parcial con el valor legado 'ACTIVO'.
-- Las reservas nuevas se crean como 'ACTIVA' y la vigencia incluye tambien
-- 'RENOVADA'. Sin esta correccion, un INSERT con ACTIVA no quedaria cubierto
-- por uq_registro_activo_por_dispositivo y dos reservas vigentes podrian
-- coexistir bajo concurrencia.
--
-- No se recrea la tabla ni se borra historico. Si un dispositivo tuviera a la
-- vez un ACTIVO legado y una ACTIVA/RENOVADA, el UPDATE se omite para ese
-- registro (no hay estrategia destructiva). En la semilla actual no hay filas
-- en registros_espera, asi que la normalizacion es total.

UPDATE registros_espera re
   SET estado = 'ACTIVA'
 WHERE estado = 'ACTIVO'
   AND NOT EXISTS (
         SELECT 1
           FROM registros_espera otro
          WHERE otro.dispositivo_id = re.dispositivo_id
            AND otro.id <> re.id
            AND otro.estado IN ('ACTIVA', 'RENOVADA')
       );

ALTER TABLE registros_espera
    ALTER COLUMN estado SET DEFAULT 'ACTIVA';

DROP INDEX IF EXISTS uq_registro_activo_por_dispositivo;

CREATE UNIQUE INDEX uq_registro_activo_por_dispositivo
    ON registros_espera (dispositivo_id)
 WHERE estado IN ('ACTIVA', 'RENOVADA');
