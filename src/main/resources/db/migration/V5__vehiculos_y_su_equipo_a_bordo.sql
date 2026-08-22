-- SCRUM-143 (HU-48): vincular un vehiculo con su equipo a bordo.
--
-- El vehiculo se separa del equipo porque el aparato de la cabina se cambia
-- -- se dana, se roba, se actualiza -- y el bus sigue siendo el mismo. El
-- historial pertenece al bus, no al aparato: por eso cambiar el equipo fisico
-- es un tramite de datos y no una migracion.
CREATE TABLE vehiculos (
    id            BIGSERIAL PRIMARY KEY,
    identificador VARCHAR(30) NOT NULL UNIQUE,   -- numero economico, ej. 'BUS-01'
    placa         VARCHAR(15) NOT NULL UNIQUE,
    activo        BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- En que bus va montado cada equipo. NULL = en bodega, sin asignar.
ALTER TABLE equipos
    ADD COLUMN vehiculo_id BIGINT REFERENCES vehiculos(id);

-- Un solo equipo ACTIVO por vehiculo. Mismo patron que
-- uq_registro_activo_por_dispositivo de V1: la regla se garantiza en la base y
-- no solo en el servicio. Cambiar de equipo es revocar el viejo y crear uno
-- nuevo, nunca reescribir el hash del existente (V4 lo mapea updatable=false).
-- Como consecuencia, la tabla equipos ES el historial de asignaciones de cada
-- bus y no hace falta una segunda tabla para consultarlo.
CREATE UNIQUE INDEX uq_equipo_activo_por_vehiculo
    ON equipos (vehiculo_id) WHERE estado = 'ACTIVO';

CREATE INDEX idx_equipo_vehiculo ON equipos (vehiculo_id);

-- Cada posicion queda atribuida al VEHICULO, no solo al aparato que la envio.
-- La atribucion se congela en el INSERT, asi que ningun cambio posterior en
-- equipos la puede invalidar: dar de baja un equipo y dar de alta otro no
-- pierde el historico del bus. equipo_id (V4) se conserva como dato forense.
ALTER TABLE posiciones_historicas
    ADD COLUMN vehiculo_id BIGINT REFERENCES vehiculos(id);

-- Nullable a proposito: las filas anteriores a esta migracion no tienen a que
-- vehiculo pertenecer y no hay forma honesta de adivinarlo. No se inventa un
-- "vehiculo desconocido" con tal de poder poner NOT NULL: eso ensucia el dato.

-- Consulta principal: "ultima posicion del vehiculo X". DESC porque siempre se
-- pide la mas reciente. idx_posicion_ts de V1 se conserva para el endpoint
-- publico sin filtro.
CREATE INDEX idx_posicion_vehiculo_ts
    ON posiciones_historicas (vehiculo_id, registrado_en DESC);

-- Vehiculo piloto: el unico bus electrico del proyecto (ver ADR-009).
INSERT INTO vehiculos (identificador, placa) VALUES ('BUS-01', 'P-000BBB');
