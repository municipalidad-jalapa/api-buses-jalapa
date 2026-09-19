-- SCRUM-24 (HU Desarrollo-144): el GPS real reporta a Traccar y Traccar
-- reenvia cada posicion a la API.
--
-- V16: develop ya ocupa hasta V15 (V15__declaracion_pasajero_no_abordo).

-- Que equipo corresponde a cada dispositivo externo. El identificador es el
-- uniqueId del dispositivo en Traccar (normalmente el IMEI). Un dispositivo
-- apunta a un solo equipo: la restriccion unica lo garantiza en la base.
CREATE TABLE dispositivos_externos (
    id             BIGSERIAL PRIMARY KEY,
    proveedor      VARCHAR(20)  NOT NULL DEFAULT 'TRACCAR',
    identificador  VARCHAR(64)  NOT NULL,
    equipo_id      BIGINT       NOT NULL REFERENCES equipos(id),
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_dispositivo_externo UNIQUE (proveedor, identificador)
);

-- Identidad de la lectura en su sistema de origen ("traccar:<position.id>").
-- Nula para las posiciones del equipo a bordo, que no la tienen. El indice
-- parcial impide que un reenvio repetido genere una segunda fila.
ALTER TABLE posiciones_historicas
    ADD COLUMN clave_origen VARCHAR(100);

CREATE UNIQUE INDEX uq_posicion_clave_origen
    ON posiciones_historicas (clave_origen) WHERE clave_origen IS NOT NULL;
