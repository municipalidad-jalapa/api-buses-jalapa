-- EcoRuta: esquema inicial
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE rutas (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL,
    trazado geometry(LineString, 4326),
    activa  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE paradas (
    id        BIGSERIAL PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    ubicacion geometry(Point, 4326) NOT NULL,
    orden     INT NOT NULL,
    ruta_id   BIGINT NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    UNIQUE (ruta_id, orden)
);

CREATE TABLE registros_espera (
    id             BIGSERIAL PRIMARY KEY,
    dispositivo_id VARCHAR(36) NOT NULL,
    parada_id      BIGINT NOT NULL REFERENCES paradas(id),
    estado         VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_registro_activo ON registros_espera (estado, parada_id);
-- Garantia dura de "un registro activo por dispositivo" a nivel de BD
CREATE UNIQUE INDEX uq_registro_activo_por_dispositivo
    ON registros_espera (dispositivo_id) WHERE estado = 'ACTIVO';

CREATE TABLE posiciones_historicas (
    id            BIGSERIAL PRIMARY KEY,
    ubicacion     geometry(Point, 4326) NOT NULL,
    velocidad_kmh DOUBLE PRECISION,
    registrado_en TIMESTAMPTZ NOT NULL,
    recibido_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_posicion_ts ON posiciones_historicas (registrado_en);

CREATE TABLE usuarios (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol           VARCHAR(20) NOT NULL,
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);
