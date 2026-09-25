-- SCRUM-26 (HU Desarrollo-146), bloque A: opiniones del pasajero sobre el
-- servicio. El vehiculo se resuelve en el servidor al registrar y se guarda:
-- asi la opinion conserva que unidad prestaba el servicio aunque despues se
-- reasigne el bus de la ruta.
CREATE TABLE opiniones (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('QUEJA', 'COMENTARIO', 'CALIFICACION')),
    ruta_id        BIGINT       NOT NULL REFERENCES rutas(id),
    vehiculo_id    BIGINT       REFERENCES vehiculos(id),
    reserva_id     BIGINT       REFERENCES registros_espera(id) ON DELETE SET NULL,
    -- Identificador anonimo del navegador (X-Dispositivo-Id). La cuenta del
    -- pasajero, cuando exista la sesion (bloque B), se agrega en otra migracion.
    dispositivo_id VARCHAR(64)  NOT NULL,
    -- Tal como lo escribio la persona. Se neutraliza al devolverlo, no aqui.
    texto          TEXT,
    estrellas      INTEGER      CHECK (estrellas BETWEEN 1 AND 5),
    creada_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atendida_en    TIMESTAMPTZ,
    atendida_por   VARCHAR(100),
    -- Al menos uno de los dos: una opinion vacia no dice nada.
    CONSTRAINT ck_opinion_con_contenido CHECK (texto IS NOT NULL OR estrellas IS NOT NULL)
);

CREATE INDEX idx_opinion_ruta     ON opiniones (ruta_id, creada_en DESC);
CREATE INDEX idx_opinion_vehiculo ON opiniones (vehiculo_id, creada_en DESC);
CREATE INDEX idx_opinion_fecha    ON opiniones (creada_en DESC);
-- Limite de envios: cuantas opiniones mando este navegador en la ventana.
CREATE INDEX idx_opinion_dispositivo ON opiniones (dispositivo_id, creada_en DESC);
