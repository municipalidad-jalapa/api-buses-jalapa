-- HU-57: avisos de proximidad y abordaje sobre registros_espera.
--
-- La tabla de reservas ya existia (registros_espera). Aqui se alinea el estado
-- ACTIVO -> ACTIVA, se guarda la respuesta de abordaje y se anaden el token FCM
-- del dispositivo, el control de duplicados por acercamiento y el registro de
-- fallos de envio (el fallo no puede tumbar la telemetria).

UPDATE registros_espera SET estado = 'ACTIVA' WHERE estado = 'ACTIVO';

ALTER TABLE registros_espera ALTER COLUMN estado SET DEFAULT 'ACTIVA';

ALTER TABLE registros_espera
    ADD COLUMN IF NOT EXISTS subio BOOLEAN,
    ADD COLUMN IF NOT EXISTS abordaje_fuente VARCHAR(20),
    ADD COLUMN IF NOT EXISTS abordaje_en TIMESTAMPTZ;

DROP INDEX IF EXISTS uq_registro_activo_por_dispositivo;
CREATE UNIQUE INDEX uq_registro_activo_por_dispositivo
    ON registros_espera (dispositivo_id) WHERE estado IN ('ACTIVA', 'RENOVADA');

CREATE TABLE dispositivos_notificacion (
    dispositivo_id VARCHAR(36) PRIMARY KEY,
    token          VARCHAR(512) NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Un fila por (reserva, tipo): "dentro" marca si ESTE acercamiento ya aviso.
-- Al salir del radio se pone en false para que el siguiente acercamiento avise otra vez.
CREATE TABLE avisos_de_proximidad (
    id              BIGSERIAL PRIMARY KEY,
    reserva_id      BIGINT NOT NULL REFERENCES registros_espera(id) ON DELETE CASCADE,
    tipo            VARCHAR(20) NOT NULL,
    dentro          BOOLEAN NOT NULL DEFAULT FALSE,
    ultimo_envio_en TIMESTAMPTZ,
    UNIQUE (reserva_id, tipo)
);

CREATE TABLE fallos_de_aviso (
    id         BIGSERIAL PRIMARY KEY,
    reserva_id BIGINT REFERENCES registros_espera(id) ON DELETE SET NULL,
    tipo       VARCHAR(20) NOT NULL,
    detalle    TEXT,
    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
