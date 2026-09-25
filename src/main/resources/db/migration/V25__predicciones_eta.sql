-- HU-73: persistir cada prediccion de ETA junto a la llegada real, para medir
-- el error por ruta, parada y franja. El registro queda activo desde el primer
-- dia del piloto: no hay bandera que lo apague.
--
-- Originalmente V14; renumerada a V25 porque develop ya ocupa V1-V23.

CREATE TABLE predicciones_eta (
    id                BIGSERIAL PRIMARY KEY,
    ruta_id           BIGINT NOT NULL REFERENCES rutas(id),
    parada_id         BIGINT NOT NULL REFERENCES paradas(id),
    vehiculo_id       BIGINT NOT NULL REFERENCES vehiculos(id),
    eta_predicho_min  INTEGER NOT NULL,
    predicho_en       TIMESTAMPTZ NOT NULL,
    -- true = dato de prueba del generador simulado, no un ETA de HU-71
    simulada          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prediccion_vigente
    ON predicciones_eta (ruta_id, parada_id, vehiculo_id, predicho_en DESC);

CREATE TABLE llegadas_reales (
    id             BIGSERIAL PRIMARY KEY,
    prediccion_id  BIGINT NOT NULL UNIQUE REFERENCES predicciones_eta(id),
    llegada_en     TIMESTAMPTZ NOT NULL,
    error_min      DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_llegada_ruta_hora
    ON llegadas_reales (llegada_en);
