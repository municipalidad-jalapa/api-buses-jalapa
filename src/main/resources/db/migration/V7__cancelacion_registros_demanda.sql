-- SCRUM-287
-- Agrega la fecha en que una reserva fue cancelada.
-- La columna estado ya existe desde V1 y los registros existentes
-- utilizan ACTIVO como estado inicial.

ALTER TABLE registros_espera
    ADD COLUMN cancelado_en TIMESTAMPTZ NULL;