-- HU-76: registro de paradas atendidas.
--
-- Permite saber que una parada ya fue marcada
-- durante el servicio del dia y evita procesarla
-- dos veces.

CREATE TABLE IF NOT EXISTS paradas_atendidas (
    id                  BIGSERIAL PRIMARY KEY,
    ruta_id             BIGINT NOT NULL REFERENCES rutas(id),
    parada_id           BIGINT NOT NULL REFERENCES paradas(id),
    conductor_username  VARCHAR(50) NOT NULL,
    fecha_servicio      DATE NOT NULL DEFAULT CURRENT_DATE,
    marcada_en          TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_parada_atendida_por_servicio
    ON paradas_atendidas (
        ruta_id,
        parada_id,
        fecha_servicio
    );

CREATE INDEX IF NOT EXISTS idx_paradas_atendidas_ruta_fecha
    ON paradas_atendidas (
        ruta_id,
        fecha_servicio
    );