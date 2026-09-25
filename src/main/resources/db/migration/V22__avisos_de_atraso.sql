-- SCRUM-26 (HU Desarrollo-146), bloque E: el piloto avisa un atraso.
--
-- El piloto reporta que viene demorado, con el motivo y cuantos minutos
-- estima. El aviso se muestra al pasajero junto al tiempo estimado, para que
-- entienda por que el bus tarda mas de lo que dice el calculo.
--
-- El aviso no se borra al vencer: queda el historial de lo que paso en la ruta
-- (sirve al panel municipal). Lo vigente se decide por vigente_hasta y
-- cancelado_en, no borrando filas.
CREATE TABLE avisos_de_atraso (
    id             BIGSERIAL PRIMARY KEY,
    ruta_id        BIGINT       NOT NULL REFERENCES rutas(id),
    vehiculo_id    BIGINT       REFERENCES vehiculos(id),
    conductor      VARCHAR(50)  NOT NULL,
    motivo         VARCHAR(20)  NOT NULL CHECK (motivo IN ('TRAFICO', 'INCIDENTE')),
    demora_minutos INTEGER      NOT NULL CHECK (demora_minutos BETWEEN 1 AND 120),
    comentario     VARCHAR(200),
    reportado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    vigente_hasta  TIMESTAMPTZ  NOT NULL,
    cancelado_en   TIMESTAMPTZ
);

CREATE INDEX idx_aviso_atraso_ruta ON avisos_de_atraso (ruta_id, reportado_en DESC);
