-- Ocupacion del bus en el mapa del pasajero: cuantas personas caben en cada
-- vehiculo, para decir "hay lugar", "casi lleno" o "lleno".
--
-- Sin valor (NULL) el pasajero ve solo cuantos van a bordo, sin nivel. No se
-- inventa una capacidad: la carga la Municipalidad con el dato real del bus.
--   UPDATE vehiculos SET capacidad = <asientos + de pie> WHERE identificador = 'BUS-01';

ALTER TABLE vehiculos
    ADD COLUMN IF NOT EXISTS capacidad INT;

ALTER TABLE vehiculos
    ADD CONSTRAINT ck_vehiculos_capacidad CHECK (capacidad IS NULL OR capacidad > 0);
