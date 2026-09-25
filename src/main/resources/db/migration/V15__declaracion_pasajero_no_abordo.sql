-- HU-76:
-- Conserva por separado la declaracion del pasajero
-- cuando indica que NO abordo.
--
-- HU-57 utiliza subio, abordaje_fuente y abordaje_en,
-- pero esos valores pueden ser corregidos posteriormente
-- por el conductor.
--
-- Estas columnas permiten conservar la discrepancia
-- para auditoria.

ALTER TABLE registros_espera
    ADD COLUMN IF NOT EXISTS pasajero_declaro_no_abordo
        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS declaracion_no_abordo_en
        TIMESTAMPTZ NULL;