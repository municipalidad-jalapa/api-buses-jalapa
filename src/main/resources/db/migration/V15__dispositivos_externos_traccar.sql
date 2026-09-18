-- SCRUM-24 (HU Desarrollo-144): el GPS real reporta a Traccar y Traccar
-- reenvia cada posicion a la API.
--
-- Numerada V15 a proposito: V13 y V14 ya estan tomadas por ramas abiertas
-- (SCRUM-136 y HU-72/73). Deben mergearse antes que esta, o Flyway rechaza
-- las migraciones que queden por debajo de la ultima aplicada.

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
