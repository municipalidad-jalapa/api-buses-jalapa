-- Eliminar una ruta desde el panel municipal (informe de QA, panel del
-- administrador).
--
-- Igual que las paradas (V30), la fila no se borra: sus paradas, reservas,
-- paradas atendidas, predicciones y opiniones la referencian y son el historial
-- del servicio. Se marca eliminada: deja de verse en el panel, en el mapa del
-- pasajero y en el selector del conductor, y los reportes la conservan.
ALTER TABLE rutas
    ADD COLUMN eliminada_en TIMESTAMPTZ;
