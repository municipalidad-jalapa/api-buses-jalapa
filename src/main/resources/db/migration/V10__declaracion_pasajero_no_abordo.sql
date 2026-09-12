-- HU-76:
-- Se conserva por separado la declaracion del pasajero
-- cuando indica que no abordo.
--
-- Esta declaracion NO reemplaza el estado de la reserva.
-- Si el conductor posteriormente confirma el abordaje,
-- el estado final sera ABORDO y la declaracion se conserva
-- para auditoria y medicion de discrepancias.

ALTER TABLE registros_espera
    ADD COLUMN pasajero_declaro_no_abordo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN declaracion_no_abordo_en TIMESTAMPTZ NULL;