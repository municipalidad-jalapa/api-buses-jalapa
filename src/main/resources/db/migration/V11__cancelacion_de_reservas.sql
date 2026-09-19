-- HU-124: el pasajero cancela su reserva.
--
-- La reserva no se borra: pasa a CANCELADA y guarda cuando se cancelo, para que
-- quede la trazabilidad de la demanda que se solto a mano.
--
-- Venia como V7__cancelacion_registros_demanda en la rama de SCRUM-276, que
-- choco con otras cuatro V7 del sprint 5. Se renumero en el orden de integracion.

ALTER TABLE registros_espera
    ADD COLUMN cancelado_en TIMESTAMPTZ NULL;
