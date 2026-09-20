-- HU-57: avisos de proximidad y abordaje sobre registros_espera.
--
-- Se guarda la respuesta de abordaje y se anaden el token FCM del dispositivo,
-- el control de duplicados por acercamiento y el registro de fallos de envio
-- (el fallo no puede tumbar la telemetria).
--
-- Al integrar el sprint 5 se quitaron de aqui la normalizacion ACTIVO -> ACTIVA,
-- el valor por defecto del estado y la recreacion del indice unico: V7 ya hace
-- las tres cosas. Y V7 las hace bien: deja en ACTIVO las filas que chocarian con
-- el indice unico. Repetir aqui el UPDATE sin esa guarda las convertiria a ciegas
-- y la migracion fallaria en cualquier base con reservas duplicadas.

ALTER TABLE registros_espera
    ADD COLUMN IF NOT EXISTS subio BOOLEAN,
    ADD COLUMN IF NOT EXISTS abordaje_fuente VARCHAR(20),
    ADD COLUMN IF NOT EXISTS abordaje_en TIMESTAMPTZ;

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
