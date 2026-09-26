-- Eliminar una parada desde el panel municipal.
--
-- No se borra la fila: registros_espera, paradas_atendidas y predicciones_eta
-- la referencian (NOT NULL) y son el historial del servicio. Se retira: deja de
-- ser parte del recorrido (pasajero, ETA, panel del conductor) y el historial
-- conserva su nombre y su ubicacion.
ALTER TABLE paradas
    ADD COLUMN retirada_en TIMESTAMPTZ;

-- El orden es unico solo entre las paradas vigentes: la retirada libera su
-- lugar para que las siguientes se recorran.
ALTER TABLE paradas
    DROP CONSTRAINT paradas_ruta_id_orden_key;

CREATE UNIQUE INDEX uq_parada_vigente_por_orden
    ON paradas (ruta_id, orden) WHERE retirada_en IS NULL;
